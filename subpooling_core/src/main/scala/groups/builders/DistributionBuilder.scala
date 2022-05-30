package io.getblok.subpooling_core
package groups.builders

import boxes.MetadataInputBox
import global.AppParameters.NodeWallet
import groups.models.GroupBuilder

import io.getblok.subpooling_core.groups.entities.Pool
import io.getblok.subpooling_core.groups.stages.roots.DistributionRoot
import io.getblok.subpooling_core.registers.{PoolFees, PropBytes}
import org.ergoplatform.appkit.{Address, BlockchainContext, InputBox, NetworkType}

class DistributionBuilder(holdingMap: Map[MetadataInputBox, InputBox], storageMap: Map[MetadataInputBox, InputBox],
                          var inputBoxes: Option[Seq[InputBox]] = None, sendTxs: Boolean = true) extends GroupBuilder {
  var _rootStage: DistributionRoot = _
  def getRoot(ctx: BlockchainContext, wallet: NodeWallet): DistributionRoot = {
    if(_rootStage == null)
      _rootStage = new DistributionRoot(pool, ctx, wallet, inputBoxes, sendTxs)
    _rootStage
  }
  /**
   * Collect information that already exists about this Transaction Group and assign it to each subPool
   */
  override def collectGroupInfo: GroupBuilder = {
    for (subPool <- pool.subPools) {
      subPool.holdingBox = holdingMap.find(b => b._1.getId == subPool.box.getId).get._2
      subPool.storedBox = storageMap.find(b => b._1.getId == subPool.box.getId).map(o => o._2)
    }
    this
  }

  /**
   * Apply special modifications to the entire Transaction Group
   */
  override def applyModifications: GroupBuilder = {
    pool.subPools.foreach {
      s =>
        if(s.token.toString != "4342b4a582c18a0e77218f1aa2de464ae1b46ad66c30abc6328e349e624e9047"
          && s.token.toString != "3d87a6c89801af3866dcdfc318b803ca09332799870f08f12e105865d537e502"
        ) {
          s.nextFees = s.box.poolFees
          s.nextInfo = s.box.poolInfo
          s.nextOps = s.box.poolOps
        }else{
          if(s.token.toString == "4342b4a582c18a0e77218f1aa2de464ae1b46ad66c30abc6328e349e624e9047"){
            s.nextFees = new PoolFees(Map(PropBytes.ofAddress(Address.create("9fMLVMsG8U1PHqHZ8JDQ4Yn6q5wPdruVn2ctwqaqCXVLfWxfc3Q"))(NetworkType.MAINNET) -> 0))
            s.nextInfo = s.box.poolInfo
            s.nextOps = s.box.poolOps
          }else if(s.token.toString == "3d87a6c89801af3866dcdfc318b803ca09332799870f08f12e105865d537e502"){
            s.nextFees = new PoolFees(Map(
              PropBytes.ofAddress(Address.create("9h6Ao31CVSsYisf4pWTM43jv6k3BaXV3jovGfaRj9PrqfYms6Rf"))(NetworkType.MAINNET) -> 1000,
              PropBytes.ofAddress(Address.create("9fMLVMsG8U1PHqHZ8JDQ4Yn6q5wPdruVn2ctwqaqCXVLfWxfc3Q"))(NetworkType.MAINNET) -> 2000
            ))
            s.nextInfo = s.box.poolInfo
            s.nextOps = s.box.poolOps
          }
        }
    }
    this
  }

  /**
   * Execute the root transaction necessary to begin the Group's Tx Chains
   *
   * @param ctx Blockchain Context to execute root transaction in
   * @return this Group Builder, with it's subPools assigned to the correct root boxes.
   */
  override def executeRootTx(ctx: BlockchainContext, wallet: NodeWallet): GroupBuilder = {
    val stageResult = stageManager.execute[InputBox](getRoot(ctx, wallet))
    pool.subPools.foreach {
      p =>
        p.rootBox = stageResult._1(p)
    }

    this
  }

  /**
   * Finalize building of the Transaction Group
   */
  override def buildGroup: Pool = {
    pool
  }
}
