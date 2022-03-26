package groups

import app.AppParameters
import org.ergoplatform.appkit.Address
import registers.{MemberInfo, PropBytes}

case class Member(address: Address, memberInfo: MemberInfo) {
  def toDistributionValue: (PropBytes, MemberInfo) = (PropBytes.ofAddress(address)(AppParameters.networkType), memberInfo)
  def shareScore: Long = memberInfo.getScore
  def minPay: Long = memberInfo.getMinPay
  def storedPay: Long = memberInfo.getStored
  def epochsMined:  Long = memberInfo.getEpochsMined
  def minerTag: Long = memberInfo.getMinerTag
}
