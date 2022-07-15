package io.getblok.subpooling_core
package states

class TxSendException(id: String) extends RuntimeException(s"There was a critical error while sending transaction with id ${id}")