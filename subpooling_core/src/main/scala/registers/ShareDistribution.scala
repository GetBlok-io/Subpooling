package io.getblok.subpooling_core
package registers

import io.getblok.subpooling_core.global.AppParameters
import org.ergoplatform.appkit.{ErgoType, ErgoValue, NetworkType}
import sigmastate.eval.Colls
import special.collection.Coll

/**
 * Represents R4 of the Metadata Box within each subpool, mapping propositional bytes to member information
 * @param dist - Map that links PropositionalBytes to MemberInfo
 */
class ShareDistribution(val dist: Map[PropBytes, MemberInfo]) {

  def ergoType: ErgoType[(Coll[Byte], Coll[Long])]        = ErgoType.pairType(
    ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.longType())
  )

  def coll:     Coll[(Coll[Byte], Coll[Long])]            = Colls.fromArray(
    dist.map(m => (m._1.coll, m._2.coll)).toArray
  )

  def ergoVal: ErgoValue[Coll[(Coll[Byte], Coll[Long])]]  = ErgoValue.of(coll, ergoType)
  def size: Int                                           = dist.size
  def apply(propBytes: PropBytes): MemberInfo             = dist(propBytes)

  def __--(props: Iterable[PropBytes])                    = new ShareDistribution(dist -- props)
  def __++(sd: ShareDistribution)                         = new ShareDistribution(dist ++ sd.dist)
  def filter(p: ((PropBytes, MemberInfo)) => Boolean)     = new ShareDistribution(dist.filter(p))

  def keys:   Iterable[PropBytes]         = dist.keys
  def values: Iterable[MemberInfo]        = dist.values
  def head:   (PropBytes, MemberInfo)     = dist.head

  override def toString: String           = dist.mkString("DIST(", ", ", ")")
  override def equals(obj: Any): Boolean  =
    obj match {
      case asMap if obj.isInstanceOf[Map[PropBytes, MemberInfo]] =>
        asMap.asInstanceOf[Map[PropBytes, MemberInfo]] == dist
      case asColl if obj.isInstanceOf[Coll[(Coll[Byte], Coll[Long])]] =>
        asColl.asInstanceOf[Coll[(Coll[Byte], Coll[Long])]] == coll
      case asErgo if obj.isInstanceOf[ErgoValue[Coll[Byte]]] =>
        asErgo.asInstanceOf[ErgoValue[Coll[(Coll[Byte], Coll[Long])]]].getValue == coll
      case asShareDist if obj.isInstanceOf[ShareDistribution] =>
        asShareDist.asInstanceOf[ShareDistribution].coll == coll
      case _ =>
        false
    }
}

object ShareDistribution {
  def ofColl(coll: Coll[(Coll[Byte], Coll[Long])]) =
    new ShareDistribution(coll.toArray.map(sd => (PropBytes.ofColl(sd._1)(AppParameters.networkType), MemberInfo.ofColl(sd._2))).toMap)

  def ofErgo(ergoValue: ErgoValue[Coll[(Coll[Byte], Coll[Long])]])(implicit networkType: NetworkType) =
    new ShareDistribution(ergoValue.getValue.toArray.map(sd => (PropBytes.ofColl(sd._1)(AppParameters.networkType), MemberInfo.ofColl(sd._2))).toMap)
}


