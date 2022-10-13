package io.getblok.subpooling_core
package states

case class DesyncedPlasmaException(poolTag: String, localDigest: String, realDigest: String) extends RuntimeException
