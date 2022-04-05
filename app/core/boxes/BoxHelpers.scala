package io.getblok.subpooling
package core.boxes

import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}
import sigmastate.lang.exceptions.InvalidArguments
import scala.collection.JavaConverters.collectionAsScalaIterableConverter

import core.logging.LoggingHandler.loggers

/**
 * Object to help retrieve and find correct boxes for using in transactions
 */
object BoxHelpers {
  val logger: Logger = LoggerFactory.getLogger(loggers.LOG_BOX_HELPER)
  final val BOX_SELECTOR_LIMIT = 20

  /**
   * Linear search to select metadata box using given parameters
   */
  def searchMetadataFromCtx(ctx: BlockchainContext, smartPoolId: ErgoId, metadataAddress: Address, metadataValue: Long, subpoolId: Int): MetadataInputBox = {
//    logger.info("Searching for metadata box...")
//    var offset = 0
//    var boxesToSearch = ctx.getUnspentBoxesFor(metadataAddress, offset, BOX_SELECTOR_LIMIT * 2)
//    if(boxesToSearch.size() > 0) {
//      while (boxesToSearch.asScala.nonEmpty) {
//        for (box <- boxesToSearch.asScala) {
//          if (box.getValue == metadataValue) {
//            if (box.getTokens.asScala.nonEmpty) {
//              val boxTokens = box.getTokens
//              if (boxTokens.get(0).getId.toString == smartPoolId.toString && boxTokens.get(0).getValue == 1) {
//                logger.info("Metadata candidate found!")
//                val currentMetaBox = new MetadataInputBox(box, smartPoolId)
//                if (currentMetaBox.getSubpoolId == subpoolId.toLong) {
//                  return currentMetaBox
//                }
//              }
//            }
//          }
//        }
//        offset = offset + boxesToSearch.size()
//        boxesToSearch = ctx.getUnspentBoxesFor(metadataAddress, offset, BOX_SELECTOR_LIMIT * 2)
//      }
//    }
    throw new InvalidArguments("Could not find any box with given parameters!")

  }

  def minBox(boxOne: InputBox, boxTwo: InputBox): InputBox ={
    if(boxOne.getValue <= boxTwo.getValue){
      boxOne
    }else{
      boxTwo
    }
  }
  def maxBox(boxOne: InputBox, boxTwo: InputBox): InputBox ={
    if(boxOne.getValue >= boxTwo.getValue){
      boxOne
    }else{
      boxTwo
    }
  }
  def sumBoxes(boxList: List[InputBox]): Long ={
    boxList.foldLeft[java.lang.Long](0L){(z, b) => z + b.getValue}
  }

  def findExactBox(ctx: BlockchainContext, holdingAddress: Address, value: Long, boxesUsed: List[InputBox]): Option[InputBox] = {
    var offset = 0
    var boxesToSearch = ctx.getUnspentBoxesFor(holdingAddress, offset, BOX_SELECTOR_LIMIT)
    while(boxesToSearch.size() > 0) {
      for (box <- boxesToSearch.asScala) {
        if (box.getValue == value && !boxesUsed.contains(box)) {
          return Some(box)
        }
      }
      offset = offset + BOX_SELECTOR_LIMIT
      boxesToSearch = ctx.getUnspentBoxesFor(holdingAddress, offset, BOX_SELECTOR_LIMIT)
    }
    None
  }

  def findExactBox(value: Long, totalBoxes: Array[InputBox]): Option[InputBox] = {
    for(box <- totalBoxes) {
      if (box.getValue.toLong == value) {
          return Some(box)
      }
    }
    None
  }

  // Pre-load all boxes for given address into array
  def loadBoxes(ctx: BlockchainContext, holdingAddress: Address): Array[InputBox] = {
    var offset = 0
    var boxesToSearch = ctx.getUnspentBoxesFor(holdingAddress, offset, BOX_SELECTOR_LIMIT*2)
    var boxes = Array.empty[InputBox]
    while(boxesToSearch.size() > 0) {
      boxes = boxes.++(boxesToSearch.asScala)
      offset = offset + (BOX_SELECTOR_LIMIT * 2)
      boxesToSearch = ctx.getUnspentBoxesFor(holdingAddress, offset, BOX_SELECTOR_LIMIT*2)
    }
    boxes
  }


  def findIdealHoldingBoxes(ctx: BlockchainContext, holdingAddress: Address, holdingValue: Long, storedPaymentValue: Long): List[InputBox] ={
    logger.info("Searching for ideal holding boxes...")

    var offset = 0
    var boxesToSearch = ctx.getUnspentBoxesFor(holdingAddress, offset, BOX_SELECTOR_LIMIT)
    var accumulatedValue = 0L
    var boxesToReturn = List[InputBox]()
    var storedPaymentBoxes = List[InputBox]()
    var storedPaymentFound = false
    var exactHoldingFound = false
    if(boxesToSearch.size() > 0){
      while(boxesToSearch.asScala.nonEmpty && (!storedPaymentFound || !exactHoldingFound)){
        for(box <- boxesToSearch.asScala){
          if(!exactHoldingFound || !storedPaymentFound) {
            // Get exact box for stored payment, which we know must exist as long as stored payment != 0
            if (storedPaymentValue != 0 && box.getValue == storedPaymentValue && storedPaymentBoxes.isEmpty) {
              logger.info("Stored Payment Box found!")
              accumulatedValue = accumulatedValue + box.getValue
              storedPaymentBoxes = List(box)
              storedPaymentFound = true
            } else if (box.getValue == holdingValue) {
              // Try to get exact box for holding value
              logger.info("Exact holding box found!")
              accumulatedValue = accumulatedValue + box.getValue
              boxesToReturn = List(box)
              exactHoldingFound = true
              if(storedPaymentValue == 0){
                return boxesToReturn
              }

            }else if(box.getValue == holdingValue + storedPaymentValue){
              logger.info("Exact box for holding and storage found!")
              return List(box)
            }

            if (boxesToReturn.nonEmpty) {
              // If we have exact values, lets return them
              if(box.getValue == storedPaymentValue && storedPaymentFound){
                logger.info("Not adding stored payment box to boxesToReturn")
              } else if (sumBoxes(boxesToReturn) == holdingValue && (storedPaymentValue == 0 || sumBoxes(storedPaymentBoxes) == storedPaymentValue)) {
                logger.info("Exact holding input boxes found!")
                return boxesToReturn ++ storedPaymentBoxes
              } else {
                if (sumBoxes(boxesToReturn) == holdingValue) {
                  exactHoldingFound = true
                } else if (sumBoxes(boxesToReturn) < holdingValue) {
                  // If we're still less than holding value, then append
                  boxesToReturn = boxesToReturn ++ List(box)
                  accumulatedValue = accumulatedValue + box.getValue
                } else if (sumBoxes(boxesToReturn) > holdingValue) {
                  // If our sumBoxes is > holding value, lets find the ideal box list
                  // by finding the list with the minimum holding value that is still >= the actual holding value
                  if(storedPaymentFound){
                    return boxesToReturn++storedPaymentBoxes
                  }else if(sumBoxes(boxesToReturn) >= holdingValue + storedPaymentValue){
                    return boxesToReturn
                  }else if(sumBoxes(boxesToReturn) < holdingValue + storedPaymentValue){
                    exactHoldingFound = true
                  }
                  accumulatedValue = sumBoxes(boxesToReturn) + sumBoxes(storedPaymentBoxes)
                }
              }
              logger.info(s"Current accumulated value for ideal holding boxes: $accumulatedValue")
            } else {
              // If our box list is currently empty, lets set it to be just the first box we find
              boxesToReturn = List(box)
              accumulatedValue = accumulatedValue + box.getValue
            }
          }
        }
        offset = boxesToSearch.size
        boxesToSearch = ctx.getUnspentBoxesFor(holdingAddress, offset, BOX_SELECTOR_LIMIT)
      }
      boxesToReturn++storedPaymentBoxes
    }else{
      boxesToReturn++storedPaymentBoxes
    }


  }

//  def getValAfterFees(lastConsensus: ShareConsensus, lastPoolFees: PoolFees, heldValue: Long, newConsensus: ShareConsensus): Long = {
//
//    val currentTxFee = newConsensus.nValue.length * Parameters.MinFee
//
//    val totalOwedPayouts =
//      lastConsensus.nValue.toArray.filter((consVal: (Coll[Byte], Coll[Long])) => consVal._2(2) < consVal._2(1))
//        .foldLeft(0L){(accum: Long, consVal: (Coll[Byte], Coll[Long])) => accum + consVal._2(2)}
//    val totalRewards = heldValue - totalOwedPayouts
//    val feeList: Coll[(Coll[Byte], Long)] = lastPoolFees.nValue.map{
//      // Pool fee is defined as x/1000 of total inputs value.
//      (poolFee: (Coll[Byte], Int)) =>
//        val feeAmount: Long = (poolFee._2.toLong * totalRewards)/1000L
//        val feeNoDust: Long = feeAmount - (feeAmount % Parameters.MinFee)
//        (poolFee._1 , feeNoDust)
//
//    }
//    // Total amount in holding after pool fees and tx fees.
//    // This is the total amount of ERG to be distributed to pool members
//    val totalValAfterFees = (feeList.toArray.foldLeft(totalRewards){
//      (accum: Long, poolFeeVal: (Coll[Byte], Long)) => accum - poolFeeVal._2
//    })- currentTxFee
//
//    totalValAfterFees
//  }

  def removeDust(value: Long): Long = {
    value - (value % Parameters.MinFee)
  }

  /*
   * Finds exact box with given token and minimum amount of said token
   */
  def findExactTokenBox(ctx: BlockchainContext, address: Address, tokenId: ErgoId, minAmount: Long): Option[InputBox] ={
    var offset = 0
    var boxesToSearch = ctx.getUnspentBoxesFor(address, offset, BOX_SELECTOR_LIMIT)
    while(boxesToSearch.size() > 0) {
      for (box <- boxesToSearch.asScala) {
        if (box.getTokens.size() > 0) {
          for(token <- box.getTokens.asScala) {
            if(token.getId == tokenId && token.getValue >= minAmount)
              return Some(box)
          }
        }
      }
      offset = offset + BOX_SELECTOR_LIMIT
      boxesToSearch = ctx.getUnspentBoxesFor(address, offset, BOX_SELECTOR_LIMIT)
    }
    None
  }

  def findAllTokenBoxes(ctx: BlockchainContext, address: Address, tokenId: ErgoId): (Array[InputBox], Long) ={
    var offset = 0
    var boxesToSearch = ctx.getUnspentBoxesFor(address, offset, BOX_SELECTOR_LIMIT)
    var tokenBoxes = Array[InputBox]()
    var tokensFound = 0L
    while(boxesToSearch.size() > 0) {
      for (box <- boxesToSearch.asScala) {
        if (box.getTokens.size() > 0) {
          for(token <- box.getTokens.asScala) {
            if(token.getId == tokenId) {
              tokensFound = tokensFound + token.getValue
              tokenBoxes = tokenBoxes ++ Array(box)
            }
          }
        }
      }
      offset = offset + BOX_SELECTOR_LIMIT
      boxesToSearch = ctx.getUnspentBoxesFor(address, offset, BOX_SELECTOR_LIMIT)
    }
    (tokenBoxes, tokensFound)
  }




}
