package boxes.builders

import boxes.HoldingOutBox
import org.ergoplatform.appkit.{ErgoContract, ErgoId, ErgoToken, ErgoValue, OutBoxBuilder}

class HoldingBuilder(outputBuilder: OutBoxBuilder){
  var registerList: Array[ErgoValue[_]] = new Array[ErgoValue[_]](5)
  var tokenList: List[ErgoToken] = List[ErgoToken]()
  var boxValue: Long = 0L
  var boxContract: ErgoContract = _
  var boxCreationHeight: Int = _
  var forMiner = false
  def value(value: Long): HoldingBuilder = {  boxValue = value; this}

  def contract(contract: ErgoContract): HoldingBuilder = {boxContract = contract; this}

  def tokens(tokens: ErgoToken*): HoldingBuilder = {tokenList = tokenList++List(tokens:_*); this}

  def isMiner: Boolean = forMiner

  def forMiner(b: Boolean): HoldingBuilder = { forMiner = b; this }

  def getBuilder: OutBoxBuilder = outputBuilder
  /**
   * Custom set registers
   * @param ergoValues register registers to set
   * @return Returns this template builder
   */
  def registers(ergoValues: ErgoValue[_]*): HoldingBuilder = {
    registerList = ergoValues.toArray
    this
  }

  def creationHeight(height: Int): HoldingBuilder = {
    boxCreationHeight = height
    this
  }

  def build: HoldingOutBox = {
    outputBuilder.value(boxValue);
    outputBuilder.registers(registerList: _*)
    outputBuilder.creationHeight(boxCreationHeight)
    outputBuilder.contract(boxContract)
    new HoldingOutBox(outputBuilder.build())
  }
}
