package group_tests


import group_tests.MockData.creatorAddress
import group_tests.groups.GroupManager
import group_tests.groups.entities.{Member, Pool, Subpool}
import org.ergoplatform.appkit.Parameters
import org.scalatest.funsuite.AnyFunSuite
import registers.MemberInfo

import scala.collection.mutable.ArrayBuffer

class GroupInitSuite extends AnyFunSuite{
  test("Make Subpool"){
    new Subpool(buildGenesisBox(Parameters.OneErg, 0))
  }

  test("Make Pool"){
    val subpool = new Subpool(buildGenesisBox(Parameters.OneErg, 0))
    new Pool(ArrayBuffer(subpool))
  }

  test("Make Member"){
    Member(creatorAddress, new MemberInfo(Array(100, Parameters.MinFee, 0, 0, 0)))
  }

}
