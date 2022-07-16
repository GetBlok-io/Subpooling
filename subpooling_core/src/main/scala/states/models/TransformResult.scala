package io.getblok.subpooling_core
package states.models

import io.getblok.subpooling_core.states.models.CommandTypes.Command
import org.ergoplatform.appkit.SignedTransaction
import io.getblok.getblok_plasma.collections.Manifest
case class TransformResult(nextState: State, transaction: SignedTransaction, data: Seq[PlasmaMiner], command: Command,
                           manifest: Option[Manifest] = None, step: Int, commandState: CommandState) {
  def id: String = transaction.getId.replace("\"", "")
}
