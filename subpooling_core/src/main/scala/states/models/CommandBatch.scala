package io.getblok.subpooling_core
package states.models

import org.ergoplatform.appkit.InputBox

case class CommandBatch(inserts: Seq[InputBox], updates: Seq[InputBox], payouts: Seq[InputBox])
