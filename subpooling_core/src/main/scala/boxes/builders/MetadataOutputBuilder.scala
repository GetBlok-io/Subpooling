package io.getblok.subpooling_core
package boxes.builders

import io.getblok.subpooling_core.boxes.MetadataOutBox
import io.getblok.subpooling_core.registers.MetadataRegisters
import org.ergoplatform.appkit.JavaHelpers.{JByteRType, JIntRType, JLongRType}
import org.ergoplatform.appkit._
import sigmastate.eval.Colls
import special.collection.Coll

/**
 * Outbox Builder wrapper that treats outboxes like metadata boxes
 *
 * @param outBoxBuilder - builder supplied by context to wrap
 */
class MetadataOutputBuilder(outBoxBuilder: OutBoxBuilder){

  final val asOutBoxBuilder = outBoxBuilder
  var metadataRegisters: MetadataRegisters = _
  var registerList: Array[ErgoValue[_]] = new Array[ErgoValue[_]](4)
  var subpoolToken: ErgoId = _
  var tokenList: List[ErgoToken] = List[ErgoToken]()
  var boxValue: Long = 0L
  var boxContract: ErgoContract = _
  var boxCreationHeight: Int = _

  def value(value: Long): MetadataOutputBuilder = { asOutBoxBuilder.value(value); boxValue = value; this}

  def contract(contract: ErgoContract): MetadataOutputBuilder = { asOutBoxBuilder.contract(contract); boxContract = contract; this}

  def tokens(tokens: ErgoToken*): MetadataOutputBuilder = {tokenList = tokenList++List(tokens:_*); this}


  /**
   * Custom set registers
   * @param ergoValues register registers to set
   * @return Returns this template builder
   */
  def registers(ergoValues: ErgoValue[_]*): MetadataOutputBuilder = {
    asOutBoxBuilder.registers(ergoValues: _*)
    this
  }

  def creationHeight(height: Int): MetadataOutputBuilder = {
    asOutBoxBuilder.creationHeight(height)
    boxCreationHeight = height
    this
  }

  def setSubpoolToken(id: ErgoId): MetadataOutputBuilder = {
    subpoolToken = id
    this
  }

  /**
   * Sets registers and smart pool id of metadata box, this function should only be called once.
   * @return This metadata box builder
   */
  def setMetadata(metadataRegs: MetadataRegisters): MetadataOutputBuilder = {
    metadataRegisters = metadataRegs
    registerList(0) = metadataRegs.shareDist.ergoVal
    registerList(1) = metadataRegs.feeMap.ergoVal
    registerList(2) = metadataRegs.poolInfo.ergoVal
    registerList(3) = metadataRegs.poolOps.ergoVal

    this
  }

  def build(): MetadataOutBox = {
    val completeTokenList = List[ErgoToken](new ErgoToken(subpoolToken, 1))++tokenList
    val distValue:  ErgoValue[Coll[(Coll[java.lang.Byte], Coll[java.lang.Long])]] =
      ErgoValue.of(
        Colls.fromArray(metadataRegisters.shareDist.dist.toArray
          .map(pm => Colls.fromArray(pm._1.arr).map(Iso.jbyteToByte.from) -> Colls.fromArray(pm._2.arr).map(Iso.jlongToLong.from))),
        ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.longType()))
      )
    val feeValue = {
      ErgoValue.of(
        Colls.fromArray(metadataRegisters.feeMap.fees.toArray
          .map(pm => Colls.fromArray(pm._1.arr).map(Iso.jbyteToByte.from) -> Iso.jintToInt.from(pm._2))),
        ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.integerType())
      )
    }
    val infoValue = metadataRegisters.poolInfo.ergoVal
    val opsValue = {
      ErgoValue.of(Colls.fromArray(metadataRegisters.poolOps.arr.map(p => Colls.fromArray(p.arr).map(Iso.jbyteToByte.from))),
        ErgoType.collType(ErgoType.byteType()))
    }
    asOutBoxBuilder.tokens(completeTokenList:_*)
    asOutBoxBuilder.registers(distValue, feeValue, infoValue, opsValue)
    new MetadataOutBox(asOutBoxBuilder.build(), metadataRegisters, subpoolToken)
  }

}
