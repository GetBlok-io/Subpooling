package io.getblok.subpooling_core
package states.models

import plasma.{StateBalance, StateMiner}

import org.ergoplatform.appkit.{ErgoContract, InputBox}

abstract class StateTransition(state: State, commandState: CommandState){
  def transform: State
}
