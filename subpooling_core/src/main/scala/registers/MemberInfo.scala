package io.getblok.subpooling_core
package registers

import org.ergoplatform.appkit.JavaHelpers.JLongRType
import org.ergoplatform.appkit.{ErgoType, ErgoValue, Iso}
import sigmastate.eval.Colls
import special.collection.Coll

class MemberInfo(val arr: Array[Long]) extends RegisterCollection[java.lang.Long](arr.map(Iso.jlongToLong.from)){

  final val SCORE_IDX         = 0
  final val MINPAY_IDX        = 1
  final val STORED_IDX        = 2
  final val EPOCHS_MINED_IDX  = 3
  final val MINER_TAG_IDX     = 4
  final val ADDITIONAL_START  = 5

  require(arr.length >= 4, "There must be at least 5 elements within a MemberInfo instance!")

  def ergoType: ErgoType[java.lang.Long]          = ErgoType.longType()
  def coll:     Coll[java.lang.Long]              = Colls.fromArray(arr).asInstanceOf[Coll[java.lang.Long]]
  def ergoVal:  ErgoValue[Coll[java.lang.Long]]   = ErgoValue.of(coll, ergoType)

  def getScore:       Long              = arr(SCORE_IDX)
  def getMinPay:      Long              = arr(MINPAY_IDX)
  def getStored:      Long              = arr(STORED_IDX)
  def getEpochsMined: Long              = arr(EPOCHS_MINED_IDX)
  def getMinerTag:    Long              = arr(MINER_TAG_IDX)
  def hasAdditional:  Boolean           = arr.length > ADDITIONAL_START
  def getAdditional:  Array[Long]       = arr.slice(ADDITIONAL_START, size)

  def withScore(score: Long)            = new MemberInfo(arr.updated(SCORE_IDX, score))
  def withMinPay(min: Long)             = new MemberInfo(arr.updated(MINPAY_IDX, min))
  def withStored(stored: Long)          = new MemberInfo(arr.updated(STORED_IDX, stored))
  def withEpochs(epochs: Long)          = new MemberInfo(arr.updated(EPOCHS_MINED_IDX, epochs))
  def withMinerTag(tag: Long)           = new MemberInfo(arr.updated(MINER_TAG_IDX, tag))
  def withAdditional(add: Array[Long])  = new MemberInfo(arr.slice(0, ADDITIONAL_START) ++ add)

  override def toString: String = s"MI(${getScore}, ${toErg(getMinPay)}, ${toErg(getStored)}," +
    s" ${getEpochsMined})${if(hasAdditional) getAdditional.mkString("[", ", ", "]") else ""}"

  override def equals(obj: Any): Boolean =
    obj match {
      case asArray if obj.isInstanceOf[Array[Long]] =>
        asArray.asInstanceOf[Array[Long]] sameElements arr
      case asColl if obj.isInstanceOf[Coll[java.lang.Long]] =>
        asColl.asInstanceOf[Coll[java.lang.Long]] == coll
      case asErgo if obj.isInstanceOf[ErgoValue[Coll[java.lang.Long]]] =>
        asErgo.asInstanceOf[ErgoValue[Coll[java.lang.Long]]].getValue == coll
      case asMemberInfo if obj.isInstanceOf[MemberInfo] =>
        asMemberInfo.asInstanceOf[MemberInfo].coll == coll
      case _ =>
        false
    }
}

object MemberInfo {
  def ofColl(coll: Coll[java.lang.Long]) =
    new MemberInfo(coll.asInstanceOf[Coll[Long]].toArray)

  def ofErgo(ergoValue: ErgoValue[Coll[java.lang.Long]]) =
    new MemberInfo(ergoValue.getValue.toArray.map(Iso.jlongToLong.to))
}


