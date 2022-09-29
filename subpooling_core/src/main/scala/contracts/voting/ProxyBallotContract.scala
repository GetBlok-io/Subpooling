package io.getblok.subpooling_core
package contracts.voting

import org.ergoplatform.appkit._
import sigmastate.Values
import sigmastate.eval.Colls


object ProxyBallotContract{

  // Uses arbitrary script differentiation to ensure right contracts are being used
  // TODO: Change script to false on tokensValid false
  def script(voteYes: Boolean): String =
    s"""
    {
       val tokensExist = SELF.tokens.size > 0 && INPUTS(0).tokens.size > 0

       // Arbitrary difference to ensure different proposition bytes
       val regCheck = ${if(voteYes) "INPUTS(0).R4[Long].isDefined" else "INPUTS(0).R5[Long].isDefined"}

       val tokensValid =
        if(tokensExist){
          INPUTS(0).tokens(0)._1 == const_recordingNFT
        }else{
          false
        }

       sigmaProp(tokensValid) && sigmaProp(regCheck)
    }
      """.stripMargin

  /**
   * To simplify voting process, we ensure only two distinct contracts are necessary to send votes, depending
   * if someone is voting yes or no
   */
  def generateContract(ctx: BlockchainContext, voteTokenId: ErgoId, voteYes: Boolean, recordingNFT: ErgoId): ErgoContract = {

    val voteTokenBytes = ErgoValue.of(voteTokenId.getBytes)
    val recordingBytes = Colls.fromArray(recordingNFT.getBytes)
    val constants = ConstantsBuilder.create()
      .item("const_recordingNFT", recordingBytes)
      .build()
    val contract: ErgoContract = ctx.compileContract(constants, script(voteYes))
    contract
  }

}