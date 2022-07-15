package io.getblok.subpooling_core
package states.models

import io.getblok.subpooling_core.states.models.CommandTypes.Command
import org.ergoplatform.appkit.{ErgoContract, InputBox}

case class CommandState(box: InputBox, data: Seq[PlasmaMiner], commandType: Command)
