package actors

import actors.GroupRequestHandler.{DistributionResponse, ExecuteDistribution, PoolData}
import akka.actor.{Actor, Props}
import configs.NodeConfig
import io.getblok.subpooling_core.boxes.MetadataInputBox
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.command.{CommandContract, PKContract}
import io.getblok.subpooling_core.contracts.holding.{HoldingContract, SimpleHoldingContract}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.groups.{DistributionGroup, GroupManager}
import io.getblok.subpooling_core.groups.builders.DistributionBuilder
import io.getblok.subpooling_core.groups.entities.{Pool, Subpool}
import io.getblok.subpooling_core.groups.selectors.LoadingSelector
import io.getblok.subpooling_core.persistence.models.Models.{Block, PoolMember, PoolPlacement, PoolState}
import org.ergoplatform.appkit.{BlockchainContext, ErgoClient, ErgoId, NetworkType}
import play.api.libs.json.Json
import play.api.{Configuration, Logger}

import javax.inject.Inject
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext

class GroupRequestHandler @Inject()(config: Configuration) extends Actor{

  private val nodeConfig             = new NodeConfig(config)
  private val ergoClient: ErgoClient = nodeConfig.getClient
  private val wallet:     NodeWallet = nodeConfig.getNodeWallet
  private val logger:     Logger     = Logger("GroupRequestHandler")


  override def receive: Receive = {
    case ExecuteDistribution(poolTag: String, poolStates: Seq[PoolState],
      placements: Seq[PoolPlacement], block: Block) =>
        logger.info(s"Received distribution request for pool with tag $poolTag for block ${block.blockheight}")

        ergoClient.execute{
          ctx =>
            implicit val networkType: NetworkType = ctx.getNetworkType

            val poolData = constructFromState(ctx, poolStates)
            val pool     = poolData.pool
            val subPools = pool.subPools

            val placedSubpools = subPools.filter(s => placements.exists(p => p.subpool_id == s.id))
            val holdingMap = placedSubpools.map(s => s.box -> ctx.getBoxesById(placements.find(p => p.subpool_id == s.id).get.holding_id).head).toMap

            val placedWithStorage = placedSubpools.filter(s => poolStates.exists(p => s.id == p.subpool_id && p.stored_id != "none"))
            val storageMap = placedWithStorage.map(s => s.box -> ctx.getBoxesById(poolStates.find(p => s.id == p.subpool_id).get.stored_id).head).toMap

            logger.warn("Using default contracts during group execution!")

            val metadataContract = MetadataContract.generateMetadataContract(ctx)
            // TODO: Parameterize holding and command contracts
            val holdingContract: HoldingContract = SimpleHoldingContract.generateHoldingContract(ctx, metadataContract.getAddress, ErgoId.create(poolTag))
            val commandContract: CommandContract = new PKContract(wallet.p2pk)


            val standard  = new LoadingSelector(placements.toArray)
            val builder   = new DistributionBuilder(holdingMap, storageMap)
            val group     = new DistributionGroup(pool, ctx, wallet, commandContract, holdingContract)

            val groupManager = new GroupManager(group, builder, standard)

            logger.info("Group information collected, now initiating Group Execution")

            groupManager.initiate()

            if(groupManager.isSuccess){
              logger.info("Group execution completed successfully")
              val poolMembers = group.getNextPoolMembers(block)
              logger.info(s"Returning ${poolMembers.length} PoolMembers at global epoch ${poolMembers.head.g_epoch} for pool $poolTag")
              val poolStates  = group.getNextStates(block)
              sender() ! DistributionResponse(poolMembers.toArray, poolStates)
            }else{
              logger.warn(s"Group execution for pool $poolTag failed, returning empty members list")
              sender() ! DistributionResponse(Array.empty[PoolMember], Array.empty[PoolState])
            }

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
}

object GroupRequestHandler {
  def props: Props = Props[GroupRequestHandler]

  /**
   * Basic pool data, with pool object, along with original subpools, and metadata boxes used to create the pool
   */
  case class PoolData(pool: Pool, subPools: Array[Subpool], metadata: Array[MetadataInputBox])

  // Received Messages
  case class ExecuteDistribution(poolTag: String, poolStates: Seq[PoolState], placements: Seq[PoolPlacement], block: Block)

  // Responses
  case class DistributionResponse(nextMembers: Array[PoolMember], nextStates: Array[PoolState])
}
