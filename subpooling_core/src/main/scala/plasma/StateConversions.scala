package io.getblok.subpooling_core
package plasma

import com.google.common.primitives.{Ints, Longs}
import io.getblok.getblok_plasma.ByteConversion

object StateConversions {

  implicit val minerConversion: ByteConversion[PartialStateMiner] = new ByteConversion[PartialStateMiner] {
    override def convertToBytes(t: PartialStateMiner): Array[Byte] = t.bytes

    override def convertFromBytes(bytes: Array[Byte]): PartialStateMiner = PartialStateMiner(bytes)
  }

  implicit val scoreConversion: ByteConversion[StateScore] = new ByteConversion[StateScore] {
    override def convertToBytes(t: StateScore): Array[Byte] = t.toBytes

    override def convertFromBytes(bytes: Array[Byte]): StateScore = StateScore(Longs.fromByteArray(bytes.slice(0, 8)), getPaid(bytes.slice(8, 9).head))
  }

  implicit val balanceConversion: ByteConversion[SingleBalance] = new ByteConversion[SingleBalance] {
    override def convertToBytes(t: SingleBalance): Array[Byte] = t.toBytes

    override def convertFromBytes(bytes: Array[Byte]): SingleBalance = SingleBalance(Longs.fromByteArray(bytes.slice(0, 8)))
  }

  implicit val dualBalanceConversion: ByteConversion[DualBalance] = new ByteConversion[DualBalance] {
    override def convertToBytes(t: DualBalance): Array[Byte] = t.toBytes

    override def convertFromBytes(bytes: Array[Byte]): DualBalance = DualBalance(Longs.fromByteArray(bytes.slice(0, 8)), Longs.fromByteArray(bytes.slice(8, 16)))
  }

  def getPaid(byte: Byte): Boolean = {
   byte match {
     case 0 =>
       false
     case 1 =>
       true
     case _ =>
       throw new Exception("A payment byte was serialized incorrectly!")
   }
  }
}
