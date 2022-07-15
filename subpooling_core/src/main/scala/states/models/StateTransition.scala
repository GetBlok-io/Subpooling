package io.getblok.subpooling_core
package states.models

import plasma.{StateBalance, StateMiner}

import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import org.ergoplatform.appkit.{BlockchainContext, ErgoContract, InputBox, SignedTransaction}

import scala.util.Try

abstract class StateTransition(val ctx: BlockchainContext, val wallet: NodeWallet, val commandState: CommandState){
  def transform(state: State): Try[TransformResult]
}
