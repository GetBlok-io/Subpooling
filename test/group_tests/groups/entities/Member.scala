package io.getblok.subpooling
package group_tests.groups.entities

import global.AppParameters

import core.registers.{MemberInfo, PropBytes}
import org.ergoplatform.appkit.Address

case class Member(address: Address, memberInfo: MemberInfo) {
  def toDistributionValue: (PropBytes, MemberInfo) = (PropBytes.ofAddress(address)(AppParameters.networkType), memberInfo)

  def shareScore: Long = memberInfo.getScore

  def minPay: Long = memberInfo.getMinPay

  def storedPay: Long = memberInfo.getStored

  def epochsMined: Long = memberInfo.getEpochsMined

  def minerTag: Long = memberInfo.getMinerTag
}
