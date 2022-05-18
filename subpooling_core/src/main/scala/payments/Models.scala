package io.getblok.subpooling_core
package payments

object Models {
  sealed trait PaymentType
  object PaymentType {
    case object PPLNS_WINDOW extends PaymentType // pplns with window

    case object PPS_WINDOW extends PaymentType // pps with window

    case object PPS_NUM_WINDOW extends PaymentType // pps number with window

    case object EQUAL_PAY extends PaymentType // equal pay

    case object PROP_MAX_ROUND extends PaymentType // PROP with max round length

    case object SOLO_SHARES extends PaymentType
  }

}
