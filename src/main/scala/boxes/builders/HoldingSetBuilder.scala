package boxes.builders

import boxes.HoldingOutBox
import org.ergoplatform.appkit.{ErgoContract, ErgoId, ErgoToken, ErgoValue, OutBoxBuilder}

class HoldingSetBuilder(outputBuilder: OutBoxBuilder){
  var registerList: Array[ErgoValue[_]] = new Array[ErgoValue[_]](5)
  var tokenList: List[ErgoToken] = List[ErgoToken]()
  var boxValue: Long = 0L
  var boxContract: ErgoContract = _
  var boxCreationHeight: Int = _
  var forMiner = false
  def value(value: Long): HoldingSetBuilder = {  boxValue = value; this}

  def contract(contract: ErgoContract): HoldingSetBuilder = {boxContract = contract; this}

  def tokens(tokens: ErgoToken*): HoldingSetBuilder = {tokenList = tokenList++List(tokens:_*); this}

  def isMiner: Boolean = forMiner

  def forMiner(b: Boolean): HoldingSetBuilder = { forMiner = b; this }

  def getBuilder: OutBoxBuilder = outputBuilder
  /**
   * Custom set registers
   * @param ergoValues register registers to set
   * @return Returns this template builder
   */
  def registers(ergoValues: ErgoValue[_]*): HoldingSetBuilder = {
    registerList = ergoValues.toArray
    this
  }

  def creationHeight(height: Int): HoldingSetBuilder = {
    boxCreationHeight = height
    this
  }

  def build: HoldingOutBox = {
    outputBuilder.value(boxValue);
    // outputBuilder.registers(registerList: _*)
    // outputBuilder.creationHeight(boxCreationHeight)
    outputBuilder.contract(boxContract)
    new HoldingOutBox(outputBuilder.build())
  }
}
