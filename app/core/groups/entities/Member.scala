package io.getblok.subpooling
package core.groups.entities

import core.registers.{MemberInfo, PropBytes}
import global.AppParameters
import org.ergoplatform.appkit.Address

case class Member(address: Address, memberInfo: MemberInfo) {
  def toDistributionValue: (PropBytes, MemberInfo) = (PropBytes.ofAddress(address)(AppParameters.networkType), memberInfo)

  def shareScore: Long = memberInfo.getScore

  def minPay: Long = memberInfo.getMinPay

  def storedPay: Long = memberInfo.getStored

  def epochsMined: Long = memberInfo.getEpochsMined

  def minerTag: Long = memberInfo.getMinerTag
}
