package actors

import actors.GroupRequestHandler._
import akka.actor.{Actor, Props}
import configs.NodeConfig
import io.getblok.subpooling_core.boxes.{EmissionsBox, ExchangeEmissionsBox, MetadataInputBox, ProportionalEmissionsBox}
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.command.{CommandContract, PKContract}
import io.getblok.subpooling_core.contracts.emissions.{EmissionsContract, ExchangeContract, ProportionalEmissionsContract}
import io.getblok.subpooling_core.contracts.holding.{AdditiveHoldingContract, HoldingContract, SimpleHoldingContract, TokenHoldingContract}
import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.groups.{DistributionGroup, GroupManager, HoldingGroup}
import io.getblok.subpooling_core.groups.builders.{DistributionBuilder, HoldingBuilder}
import io.getblok.subpooling_core.groups.entities.{Member, Pool, Subpool}
import io.getblok.subpooling_core.groups.models.{GroupBuilder, GroupSelector, TransactionGroup, TransactionStage}
import io.getblok.subpooling_core.groups.selectors.{LoadingSelector, SelectionParameters, StandardSelector}
import io.getblok.subpooling_core.groups.stages.roots.{EmissionRoot, ExchangeEmissionsRoot, HoldingRoot, ProportionalEmissionsRoot}
import io.getblok.subpooling_core.persistence.models.Models.{Block, PoolBlock, PoolInformation, PoolMember, PoolPlacement, PoolState}
import models.DatabaseModels.SPoolBlock
import org.ergoplatform.appkit.{Address, BlockchainContext, BoxOperations, ErgoClient, ErgoClientException, ErgoId, ErgoToken, InputBox, NetworkType, Parameters, RestApiErgoClient}
import play.api.libs.json.Json
import play.api.{Configuration, Logger}
import utils.ConcurrentBoxLoader.BatchSelection
import utils.EmissionTemplates

import javax.inject.Inject
import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Try}

class GroupRequestHandler @Inject()(config: Configuration) extends Actor{

  private val nodeConfig             = new NodeConfig(config)
  private val ergoClient: ErgoClient = nodeConfig.getClient
  private val wallet:     NodeWallet = nodeConfig.getNodeWallet
  private val logger:     Logger     = Logger("GroupRequestHandler")
  logger.info("Initiating GroupRequestHandler")

  override def receive: Receive = {
    case groupRequest: GroupRequest =>
        Try {
          groupRequest match {
            case ExecuteDistribution(distributionComponents: DistributionComponents, block: SPoolBlock) =>
              Try {
                val poolTag = distributionComponents.poolTag
                logger.info(s"Received distribution request for pool with tag $poolTag for block ${block.blockheight}")
                ergoClient.execute {
                  ctx =>
                    implicit val networkType: NetworkType = ctx.getNetworkType

                    logger.info(s"Now initiating Group Execution")

                    val manager = distributionComponents.manager
                    val group = distributionComponents.group
                    manager.initiate()
                    if (manager.isSuccess) {
                      logger.info("Group execution completed successfully")
                      val poolMembers = group.getNextPoolMembers(PoolBlock(block.id, block.blockheight, block.netDiff, block.status, block.confirmation, block.effort,
                        block.txConfirmation.get, block.miner, block.reward, block.hash.get, block.created, block.poolTag, block.gEpoch, block.updated))
                      logger.info(s"Returning ${poolMembers.length} PoolMembers at global epoch ${poolMembers.head.g_epoch} for pool $poolTag")

                      // Make initial states, then convert them to failed and success
                      // TODO: This is somewhat of a bad fix for states not having correct data by now, should move pool initiation somewhere else
                      val initStates = distributionComponents.placedStates.map(s => s.makeInitiated(poolMembers.count(m => m.subpool_id == s.subpool_id), block.blockheight))
                      val failedStates = initStates.filter(s => group.failedGroups.exists(fg => fg._1.id == s.subpool_id)).map(_.makeFailure)
                      val successStates = group.getNextStates(initStates.filter(s => group.completedGroups.exists(sg => sg._1.id == s.subpool_id)))
                      sender() ! DistributionResponse(poolMembers.toArray, successStates ++ failedStates, block)
                    } else {
                      logger.warn(s"Group execution for pool $poolTag failed, returning empty lists")
                      sender() ! DistributionResponse(Array.empty[PoolMember], Array.empty[PoolState], block)
                    }
                }
              }.recoverWith {
                case ex =>
                  logger.error("There was a critical error while evaluating distributions!", ex)
                  Failure(ex)
              }
            case ExecuteHolding(holdingComponents) =>
              ergoClient.execute {
                ctx =>
                  logger.info(s"Executing holding group for ${holdingComponents.poolTag} for blocks ${holdingComponents.batchSelection.blocks.toString()}")
                  val groupManager = holdingComponents.manager
                  val group = holdingComponents.group

                  groupManager.initiate()

                  if (groupManager.isSuccess) {
                    sender ! HoldingResponse(group.poolPlacements.map(_.copy(g_epoch = holdingComponents.batchSelection.blocks.head.gEpoch)).toArray, holdingComponents.batchSelection)
                  } else {
                    sender ! HoldingResponse(Array(), holdingComponents.batchSelection)
                  }
              }
            case ConstructDistribution(poolTag, poolStates, placements, poolInformation, block) =>
              ergoClient.execute {
                ctx =>
                  implicit val networkType: NetworkType = ctx.getNetworkType
                  var statesToUse = poolStates
                  if(poolStates.exists(ps => ps.status == PoolState.FAILURE))
                    statesToUse = poolStates.filter(ps => ps.status == PoolState.FAILURE)
                  val poolData = constructFromState(ctx, statesToUse)
                  val pool = poolData.pool
                  var subPools = pool.subPools
                  pool.globalEpoch = block.gEpoch
                  if (poolStates.exists(s => s.status == PoolState.FAILURE)) {
                    logger.warn(s"Group Manager found failed pool states, now filtering SubPools for $poolTag so" +
                      s" that failed states are executed")
                    subPools = subPools.filter(sp => poolStates.exists(s => s.status == PoolState.FAILURE && s.subpool_id == sp.id))
                  }


                  val placedSubpools = subPools.filter(s => placements.exists(p => p.subpool_id == s.id))
                  val placedStates = poolStates.filter(s => placements.exists(p => p.subpool_id == s.subpool_id))
                  val tryHoldingMap = Try(placedSubpools.map(s => s.box -> ctx.getBoxesById(placements.find(p => p.subpool_id == s.id).get.holding_id).head).toMap)
                  if (tryHoldingMap.isSuccess) {
                    val holdingMap = tryHoldingMap.get

                    val placedWithStorage = placedSubpools.filter(s => poolStates.exists(p => s.id == p.subpool_id && p.stored_id != "none"))
                    val storageMap = placedWithStorage.map(s => s.box -> ctx.getBoxesById(poolStates.find(p => s.id == p.subpool_id).get.stored_id).head).toMap

                    logger.warn("Using default contracts during group execution!")

                    val metadataContract = MetadataContract.generateMetadataContract(ctx)

                    var tokenName: Option[String] = None
                    val holdingContract: HoldingContract =
                      poolInformation.currency match {
                        case PoolInformation.CURR_ERG =>
                          SimpleHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(poolTag))
                        case PoolInformation.CURR_TEST_TOKENS =>
                          logger.info("Using test tokens for currency!")
                          TokenHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(poolTag))
                        case PoolInformation.CURR_NETA =>
                          logger.info("Using NETA for currency!")
                          TokenHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(poolTag))
                        case PoolInformation.CURR_ERG_COMET =>
                          logger.info("Using ERG+COMET for currency!")
                          tokenName = Some("COMET")
                          AdditiveHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(poolTag))
                        case _ =>
                          SimpleHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(poolTag))
                      }

                    val commandContract: CommandContract = new PKContract(wallet.p2pk)


                    val selector = new LoadingSelector(placements.toArray)
                    val builder = new DistributionBuilder(holdingMap, storageMap, sendTxs = AppParameters.sendTxs)
                    val group = new DistributionGroup(pool, ctx, wallet, commandContract, holdingContract, tokenName, sendTxs = AppParameters.sendTxs)

                    val groupManager = new GroupManager(group, builder, selector)
                    sender ! DistributionComponents(groupManager, selector, builder, group, poolTag, block, placedStates)
                  } else {
                    val failure = tryHoldingMap.failed.get
                    if (failure.isInstanceOf[ErgoClientException]) {
                      logger.warn("There was an error attempting to load holding boxes from placements!", failure)
                      logger.warn("Returning FailedPlacements to sender!")
                      sender ! FailedPlacements(block)
                    } else {
                      logger.error("There was an unknown exception thrown while grabbing holding boxes from the blockchain", failure)
                    }
                  }
              }

            case ConstructHolding(poolTag, poolStates: Seq[PoolState], membersWithInfo: Array[Member], optLastPlacements: Option[Seq[PoolPlacement]],
            poolInformation, batchSelection, reward) =>
              ergoClient.execute {
                ctx =>

                  implicit val networkType: NetworkType = ctx.getNetworkType
                  val holdingSetup = {
                    if (optLastPlacements.isDefined) {
                      if (optLastPlacements.get.nonEmpty) {
                        if (optLastPlacements.get.head.block < batchSelection.blocks.head.blockheight) {
                          setupHolding(poolTag, poolStates, membersWithInfo, optLastPlacements, batchSelection.blocks.head)
                        } else {
                          logger.warn("Last placements block was >= current block used for placements")
                          logger.warn("Now setting up placements using blockchain data")
                          setupHolding(poolTag, poolStates, membersWithInfo, None, batchSelection.blocks.head)
                        }
                      } else {
                        logger.warn("Last placements were empty!, using blockchain data to construct holding")
                        setupHolding(poolTag, poolStates, membersWithInfo, None, batchSelection.blocks.head)
                      }
                    } else {
                      logger.info("Last placements were not found, using blockchain data to construct holding")
                      setupHolding(poolTag, poolStates, membersWithInfo, None, batchSelection.blocks.head)
                    }
                  }
                  val modifiedPool = holdingSetup.modifiedPool
                  val modifiedMembers = holdingSetup.modifiedMembers
                  val metadataContract = MetadataContract.generateMetadataContract(ctx)
                  val currencyComponents = poolInformation.currency match {

                    case PoolInformation.CURR_ERG =>
                      val holdingContract = SimpleHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(poolTag))
                      val root = new HoldingRoot(modifiedPool, ctx, wallet, holdingContract, AppParameters.getBaseFees(reward),
                        sendTxs = AppParameters.sendTxs)
                      val builder = new HoldingBuilder(reward, holdingContract, AppParameters.getBaseFees(reward), root)
                      GroupCurrencyComponents(holdingContract, root, builder)

                    case PoolInformation.CURR_TEST_TOKENS =>
                      logger.info("Using test tokens for currency!")
                      val holdingContract = TokenHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(poolTag))
                      val emissionsContract = EmissionsContract.generate(ctx, wallet.p2pk, Address.create(poolInformation.creator), holdingContract)
                      val emissionInput = ctx.getCoveringBoxesFor(emissionsContract.getAddress,
                        Parameters.MinFee, Seq(new ErgoToken(poolInformation.emissions_id, 1L)).asJava).getBoxes
                        .asScala.toSeq
                        .filter(i => i.getTokens.size() > 0)
                        .filter(i => i.getTokens.get(0).getId.toString == poolInformation.emissions_id).head
                      val emissionsBox = new EmissionsBox(emissionInput, emissionsContract)
                      logger.info(s"An emissions box was found! $emissionsBox")
                      val root = new EmissionRoot(modifiedPool, ctx, wallet, holdingContract, reward,
                        AppParameters.getBaseFees(reward), emissionsBox, sendTxs = AppParameters.sendTxs)
                      val builder = new HoldingBuilder(emissionsBox.emissionReward.value, holdingContract, AppParameters.getBaseFees(reward), root)
                      GroupCurrencyComponents(holdingContract, root, builder)

                    case PoolInformation.CURR_NETA =>
                      logger.info("Using NETA tokens for currency!")
                      val holdingContract = TokenHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(poolTag))
                      val template = EmissionTemplates.getNETATemplate(ctx.getNetworkType)
                      val emissionsContract = ExchangeContract.generate(ctx, wallet.p2pk, template.swapAddress, holdingContract, template.lpNFT, template.distToken)
                      logger.info(s"Emissions Contract: ${emissionsContract.getAddress}")
                      val potentialBoxes = ctx.getCoveringBoxesFor(emissionsContract.getAddress, 0L, Seq(new ErgoToken(poolInformation.emissions_id, 1L)).asJava).getBoxes

                      logger.info("Potential boxes: ")
                      potentialBoxes.asScala.map(_.toJson(true)).foreach(j => logger.info(j))

                      val emissionInput = ctx.getCoveringBoxesFor(emissionsContract.getAddress,
                        0L, Seq(new ErgoToken(poolInformation.emissions_id, 1L)).asJava).getBoxes
                        .asScala.toSeq
                        .filter(i => i.getTokens.size() > 0)
                        .filter(i => i.getTokens.get(0).getId.toString == poolInformation.emissions_id).head


                      val emissionsBox = new ExchangeEmissionsBox(emissionInput, emissionsContract)
                      logger.info(s"An exchange emissions box was found! $emissionsBox")
                      val root = new ExchangeEmissionsRoot(modifiedPool, ctx, wallet, holdingContract, reward,
                        AppParameters.getBaseFees(reward), emissionsBox, sendTxs = AppParameters.sendTxs)
                      val builder = new HoldingBuilder(reward, holdingContract, AppParameters.getBaseFees(reward), root)
                      GroupCurrencyComponents(holdingContract, root, builder)

                    case PoolInformation.CURR_ERG_COMET =>
                      logger.info("Using ERG+COMET tokens for currency!")
                      val holdingContract = AdditiveHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(poolTag))
                      val template = EmissionTemplates.getCOMETTemplate(ctx.getNetworkType)
                      val emissionsContract = ProportionalEmissionsContract.generate(ctx, wallet.p2pk, template.swapAddress, holdingContract, template.distToken, template.decimalPlaces)
                      val emissionInput = ctx.getCoveringBoxesFor(emissionsContract.getAddress,
                        0L, Seq(new ErgoToken(poolInformation.emissions_id, 1L)).asJava).getBoxes
                        .asScala.toSeq
                        .filter(i => i.getTokens.size() > 0)
                        .filter(i => i.getTokens.get(0).getId.toString == poolInformation.emissions_id).head
                      val emissionsBox = new ProportionalEmissionsBox(emissionInput, emissionsContract)
                      logger.info(s"An exchange emissions box was found! $emissionsBox")
                      val root = new ProportionalEmissionsRoot(modifiedPool, ctx, wallet, holdingContract, reward,
                        AppParameters.getBaseFees(reward), emissionsBox, sendTxs = AppParameters.sendTxs)
                      val builder = new HoldingBuilder(reward, holdingContract, AppParameters.getBaseFees(reward), root)
                      GroupCurrencyComponents(holdingContract, root, builder)
                  }

                  val standard = new StandardSelector(modifiedMembers,
                    SelectionParameters(-Math.abs(poolInformation.epoch_kick), Math.min(Math.abs(poolInformation.max_members), 10)))
                  val group = new HoldingGroup(modifiedPool, ctx, wallet, batchSelection.blocks.head.blockheight, currencyComponents.holdingContract)
                  val groupManager = new GroupManager(group, currencyComponents.builder, standard)
                  sender ! HoldingComponents(groupManager, standard, currencyComponents.builder, currencyComponents.root, group, poolTag, batchSelection)
              }
          }
        }.recoverWith{
          case ex =>
            logger.error("There was a fatal exception thrown by this GroupRequestHandler!", ex)
            Failure(ex)
        }
  }

  def constructFromState(ctx: BlockchainContext, poolStates: Seq[PoolState]): PoolData = {
    // Create pool
    val poolTag = poolStates.head.subpool
    val metadataBoxes = poolStates.map(s => new MetadataInputBox(ctx.getBoxesById(s.box).head, ErgoId.create(poolTag)))
    val subPools = ArrayBuffer() ++= metadataBoxes.map(m => new Subpool(m))
    val pool = new Pool(subPools)
    PoolData(pool, subPools.toArray, metadataBoxes.toArray)
  }

  def setupHolding(poolTag: String, poolStates: Seq[PoolState], membersWithMinPay: Array[Member], optLastPlacements: Option[Seq[PoolPlacement]], block: SPoolBlock): HoldingSetup = {
    ergoClient.execute{
      ctx =>
        // Create pool
        val metadataBoxes = poolStates.map(s => new MetadataInputBox(ctx.getBoxesById(s.box).head, ErgoId.create(poolTag)))
        val subPools = ArrayBuffer() ++= metadataBoxes.map(m => new Subpool(m))
        val pool = new Pool(subPools)


        // Updated members with epochsMined added
        val updatedMembers = ArrayBuffer.empty[Member]

        // Use last placement to get most recent epochs mined
        if(optLastPlacements.isDefined){
          logger.info("Last placements were defined!")
          val allLastPlacements = optLastPlacements.get
          val lastEpochsMined = for(place <- allLastPlacements) yield place.miner -> place.epochs_mined
          // Place members based on placements
          for(member <- membersWithMinPay){
            val lastMined = lastEpochsMined.find(em => em._1 == member.address.toString)
            if(lastMined.isDefined)
              updatedMembers += member.copy(memberInfo = member.memberInfo.withEpochs(lastMined.get._2))
            else
              updatedMembers += member
          }
          // Load members and epoch based on last placements
          pool.subPools.foreach{
            s =>
              val lastPoolPlacements = allLastPlacements.filter(p => p.subpool_id == s.id)
              if(lastPoolPlacements.nonEmpty) {
                s.members = lastPoolPlacements.map(m => m.toPartialMember).toArray
                s.epoch = lastPoolPlacements.head.epoch
              }
          }
          // Set new globalEpoch based on last placement
          pool.globalEpoch = block.gEpoch
        }else{
          // If last placements not defined, take from metadata
          logger.info("Last placements were not defined, now taking from metadata!")
          for(member <- membersWithMinPay){
            val lastMined = metadataBoxes.find(m => m.shareDistribution.dist.exists(d => d._1.address == member.address))
            if(lastMined.isDefined){
              val lastEpochsMined = lastMined.get.shareDistribution.dist.find(d => d._1.address == member.address).get._2.getEpochsMined
              updatedMembers += member.copy(memberInfo = member.memberInfo.withEpochs(lastEpochsMined))
            }else{
              updatedMembers += member
            }
          }
          pool.globalEpoch = block.gEpoch
        }
        // TODO: Make pool flag (0) next Fee change epoch, it must hit up to 5 epochs before being lowered
        HoldingSetup(pool, updatedMembers.toArray)
    }
  }

}

object GroupRequestHandler {
  def props: Props = Props[GroupRequestHandler]
  trait GroupRequest
  /**
   * Basic pool data, with pool object, along with original subpools, and metadata boxes used to create the pool
   */
  case class PoolData(pool: Pool, subPools: Array[Subpool], metadata: Array[MetadataInputBox])

  // Received Messages
  case class ExecuteDistribution(distributionComponents: DistributionComponents, block: SPoolBlock) extends GroupRequest
  case class ExecuteHolding(holdingComponents: HoldingComponents) extends GroupRequest
  case class ConstructDistribution(poolTag: String, poolStates: Seq[PoolState], placements: Seq[PoolPlacement],
                                   poolInformation: PoolInformation, block: SPoolBlock) extends GroupRequest
  case class ConstructHolding(poolTag: String, poolStates: Seq[PoolState], membersWithMinPay: Array[Member],
                              optLastPlacements: Option[Seq[PoolPlacement]], poolInformation: PoolInformation, batchSelection: BatchSelection,
                              reward: Long) extends GroupRequest
  // Responses
  case class DistributionResponse(nextMembers: Array[PoolMember], nextStates: Array[PoolState], block: SPoolBlock)
  case class HoldingResponse(nextPlacements: Array[PoolPlacement], batchSelection: BatchSelection)

  case class HoldingSetup(modifiedPool: Pool, modifiedMembers: Array[Member])
  class GroupComponents(manager: GroupManager, selector: GroupSelector, builder: GroupBuilder, group: TransactionGroup, poolTag: String)

  case class DistributionComponents(manager: GroupManager, selector: LoadingSelector, builder: DistributionBuilder,
                                    group: DistributionGroup, poolTag: String, block: SPoolBlock, placedStates: Seq[PoolState])
    extends GroupComponents(manager, selector, builder, group, poolTag)

  case class FailedPlacements(block: SPoolBlock)

  case class HoldingComponents(manager: GroupManager, selector: StandardSelector, builder: HoldingBuilder, root: TransactionStage[InputBox],
                               group: HoldingGroup, poolTag: String, batchSelection: BatchSelection) extends GroupComponents(manager, selector, builder, group, poolTag)

  case class PoolParameters(poolCurrency: String, poolTokenId: String, poolTokenBox: InputBox)

  case class GroupCurrencyComponents(holdingContract: HoldingContract, root: TransactionStage[InputBox], builder: HoldingBuilder)
}
