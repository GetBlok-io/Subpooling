package registers

import org.ergoplatform.appkit.{Address, ErgoType, ErgoValue, NetworkType}
import sigmastate.Values
import sigmastate.eval.Colls
import special.collection.Coll

/**
 * RegisterCollection representing collection of longs holding pool info
 */
class PoolInfo(val arr: Array[Long]) extends RegisterCollection[Long](arr){

  final val EPOCH_IDX         = 0
  final val HEIGHT_IDX        = 1
  final val GEN_HEIGHT_IDX    = 2
  final val SUBPOOL_IDX       = 3
  final val TAG_IDX           = 4
  final val ADDITIONAL_START  = 5

  require(arr.length > 4, "There must be at least 5 elements within a PoolInfo instance!")

  def ergoType:         ErgoType[Long]        = ErgoType.longType()
  def coll:             Coll[Long]            = Colls.fromArray(arr)
  def ergoVal:          ErgoValue[Coll[Long]] = ErgoValue.of(coll, ergoType)
  def apply(idx: Int):  Long                  = arr(idx)

  def getEpoch:         Long = this(EPOCH_IDX)
  def getEpochHeight:   Long = this(HEIGHT_IDX)
  def getGenesisHeight: Long = this(GEN_HEIGHT_IDX)
  def getSubpool:       Long = this(SUBPOOL_IDX)
  def getTag:           Long = this(TAG_IDX)

  def hasAdditional:  Boolean             = arr.length > ADDITIONAL_START
  def getAdditional:  Array[Long]         = arr.slice(ADDITIONAL_START, size)

  def withEpoch(epoch: Long)              = new PoolInfo(arr.updated(EPOCH_IDX, epoch))
  def withHeight(height: Long)            = new PoolInfo(arr.updated(HEIGHT_IDX, height))
  def withGenHeight(genHeight: Long)      = new PoolInfo(arr.updated(GEN_HEIGHT_IDX, genHeight))
  def withSubpool(subpool: Long)          = new PoolInfo(arr.updated(SUBPOOL_IDX, subpool))
  def withTag(tag: Long)                  = new PoolInfo(arr.updated(TAG_IDX, tag))
  def withAdditional(add: Array[Long])    = new PoolInfo(arr.slice(0, ADDITIONAL_START) ++ add)

  override def toString: String = s"INFO[$getSubpool](${getEpoch}, ${getEpochHeight}, ${getGenesisHeight})" +
    s"#${getTag}${if(hasAdditional) getAdditional.mkString("{", ", ", "}") else ""}"

  override def equals(obj: Any): Boolean =
    obj match {
      case asArray if obj.isInstanceOf[Array[Long]] =>
        asArray.asInstanceOf[Array[Long]] sameElements arr
      case asColl if obj.isInstanceOf[Coll[Long]] =>
        asColl.asInstanceOf[Coll[Long]] == coll
      case asErgo if obj.isInstanceOf[ErgoValue[Coll[Long]]] =>
        asErgo.asInstanceOf[ErgoValue[Coll[Long]]].getValue == coll
      case asPoolInfo if obj.isInstanceOf[PoolInfo] =>
        asPoolInfo.asInstanceOf[PoolInfo].coll == coll
      case _ =>
        false
    }
}

object PoolInfo {
  def ofColl(coll: Coll[Long]) =
    new PoolInfo(coll.toArray)

  def ofErgo(ergoValue: ErgoValue[Coll[Long]]) =
    new PoolInfo(ergoValue.getValue.toArray)
}


