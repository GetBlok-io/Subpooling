package io.getblok.subpooling_core
package states.models

object CommandTypes {
  sealed trait Command

  case object INSERT extends Command
  case object UPDATE extends Command
  case object PAYOUT extends Command
  case object DELETE extends Command
  case object SETUP extends  Command
}
