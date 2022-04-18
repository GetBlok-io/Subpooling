package io.getblok.subpooling_core
package groups.stages

trait ParallelRoot {
  /**
   * Predicts total ERG value of Input boxes required to "fuel" the entire group through its phases(stages / chains)
   * @return
   */
  def predictTotalInputs: Long
}
