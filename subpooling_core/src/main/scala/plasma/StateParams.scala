package io.getblok.subpooling_core
package plasma

import io.getblok.getblok_plasma.PlasmaParameters
import sigmastate.AvlTreeFlags

object StateParams {
  val treeParams: PlasmaParameters = PlasmaParameters(32, None)
  val treeFlags: AvlTreeFlags = AvlTreeFlags.AllOperationsAllowed
  val maxVersions: Int = 100
}
