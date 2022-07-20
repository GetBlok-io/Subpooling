package plasma_utils.payments

import io.getblok.subpooling_core.persistence.models.PersistenceModels.{PoolMember, PoolPlacement}

trait PaymentProcessor {
  def processNext(placements: Seq[PoolPlacement]): Seq[PoolPlacement]
  def processFirst(poolMembers: Seq[PoolMember]): Seq[PoolPlacement]
}
