package io.getblok.subpooling_core
package states.models

import plasma.{SingleBalance, StateBalance, StateMiner}

import io.getblok.getblok_plasma.ByteConversion
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import org.ergoplatform.appkit.{BlockchainContext, ErgoContract, InputBox, SignedTransaction}

import scala.util.Try

abstract class StateTransition[T <: StateBalance](val ctx: BlockchainContext, val wallet: NodeWallet,
                                                  val commandState: CommandState)
                                                 (implicit convT: ByteConversion[T]){
  def transform(state: State[T]): Try[TransformResult[T]]
}
