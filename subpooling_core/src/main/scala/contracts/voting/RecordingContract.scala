package io.getblok.subpooling_core
package contracts.voting

import io.getblok.subpooling_core.global.AppParameters
import org.ergoplatform.appkit._
import sigmastate.Values
import sigmastate.eval.Colls

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

object RecordingContract {
  // TODO: Add vote ending height
  // TODO: Add second vote token
  val script: String =
  s"""
    {
       val inputsValid = INPUTS.forall{
        (box: Box) =>
          if(box.propositionBytes == SELF.propositionBytes || box.propositionBytes == const_voteYesBytes || box.propositionBytes == const_voteNoBytes){
            true
          }else{
            false
          }
       }

       val heightValid = HEIGHT < const_voteEndHeight

       val registersUpdated =
        if(inputsValid){
          val newYesVotes = INPUTS.fold(0L, {
            (accum: Long, box: Box) =>
              if(box.propositionBytes == const_voteYesBytes){
                if(box.tokens.size > 0){
                  if(box.tokens(0)._1 == const_voteTokenId){
                    accum + box.tokens(0)._2
                  }else{
                    accum
                  }
                }else{
                  accum
                }
              }else{
                accum
              }
          })

          val newNoVotes = INPUTS.fold(0L, {
            (accum: Long, box: Box) =>
              if(box.propositionBytes == const_voteNoBytes){
                if(box.tokens.size > 0){
                  if(box.tokens(0)._1 == const_voteTokenId){
                    accum + box.tokens(0)._2
                  }else{
                    accum
                  }
                }else{
                  accum
                }
              }else{
                accum
              }
          })

          val currentYesVotes = SELF.R4[Long].get
          val currentNoVotes = SELF.R5[Long].get

          val yesVotesUpdated = OUTPUTS(0).R4[Long].get == currentYesVotes + newYesVotes
          val noVotesUpdated = OUTPUTS(0).R5[Long].get == currentNoVotes + newNoVotes
          val recordingOutputted = OUTPUTS(0).propositionBytes == SELF.propositionBytes && OUTPUTS(0).value == SELF.value
          val outputsToCheck = OUTPUTS.slice(1, OUTPUTS.size)
          val tokensBurned = outputsToCheck.forall{(box: Box) =>
            box.tokens.size == 0
          }

          val nftPreserved = OUTPUTS(0).tokens.size == 1 && OUTPUTS(0).tokens(0)._1 == SELF.tokens(0)._1

          recordingOutputted && yesVotesUpdated && noVotesUpdated && nftPreserved && tokensBurned
        }else{
          false
        }

       sigmaProp(registersUpdated) && sigmaProp(heightValid)
    }
      """.stripMargin

  /**
   * To simplify voting process, we only allow one P2PK address to reclaim lost funds above minimum amount required.
   * @param ctx
   * @param voteTokenId
   * @param voteYes
   * @param changeAddress
   * @return
   */
  def generateContract(ctx: BlockchainContext, voteTokenId: ErgoId, voteYes: Address, voteNo: Address, voteEndHeight: Int): ErgoContract = {
    val voteYesBytes = Colls.fromArray(voteYes.getErgoAddress.script.bytes)
    val voteNoBytes = Colls.fromArray(voteNo.getErgoAddress.script.bytes)
    val voteTokenBytes = Colls.fromArray(voteTokenId.getBytes)

    val constants = ConstantsBuilder.create()
      .item("const_voteTokenId", voteTokenBytes)
      .item("const_voteYesBytes", voteYesBytes)
      .item("const_voteNoBytes", voteNoBytes)
      .item("const_voteEndHeight", voteEndHeight)
      .build()
    val contract: ErgoContract = ctx.compileContract(constants, script)
    contract
  }

  def buildNewRecordingBox(ctx: BlockchainContext, recordingId: ErgoId, recordingContract: ErgoContract, recordingValue: Long): OutBox = {
    val outB = ctx.newTxBuilder().outBoxBuilder()
    val recordingToken = new ErgoToken(recordingId, 1L)
    val zeroedVotes = ErgoValue.of(0L)
    val asOutBox = outB
      .contract(recordingContract)
      .value(recordingValue)
      .registers(zeroedVotes, zeroedVotes)
      .tokens(recordingToken)
      .build()
    asOutBox
  }

  def buildNextRecordingBox(ctx: BlockchainContext, recordingBox: InputBox, recordingContract: ErgoContract,
                            yesBoxes: Array[InputBox], noBoxes:Array[InputBox], voteTokenId: ErgoId): OutBox = {
    val outB = ctx.newTxBuilder().outBoxBuilder()
    val recordingToken = new ErgoToken(recordingBox.getTokens.get(0).getId, 1L)
    val newYesVotes = yesBoxes
      .filter(ib => ib.getTokens.size() > 0)
      .filter(ib => ib.getTokens.get(0).getId == voteTokenId)
      .map(ib => ib.getTokens.get(0).getValue)
      .sum

    val newNoVotes = noBoxes
      .filter(ib => ib.getTokens.size() > 0)
      .filter(ib => ib.getTokens.get(0).getId == voteTokenId)
      .map(ib => ib.getTokens.get(0).getValue)
      .sum


    val updatedYesVotes = ErgoValue.of(recordingBox.getRegisters.get(0).getValue.asInstanceOf[Long] + newYesVotes)
    val updatedNoVotes = ErgoValue.of(recordingBox.getRegisters.get(1).getValue.asInstanceOf[Long] + newNoVotes)


    val asOutBox = outB
      .contract(recordingContract)
      .value(recordingBox.getValue)
      .registers(updatedYesVotes, updatedNoVotes)
      .tokens(recordingToken)
      .build()
    asOutBox
  }

}
