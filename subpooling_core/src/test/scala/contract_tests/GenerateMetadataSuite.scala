package io.getblok.subpooling_core
package contract_tests

import boxes.MetadataInputBox
import contracts.MetadataContract
import transactions.{CreateSubpoolTokenTx, GenerateMultipleTx}

import org.ergoplatform.appkit.{Address, Parameters, SignedTransaction}
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class GenerateMetadataSuite extends AnyFunSuite{



  test("Token Output Box has correct value") {

    val numSubpools = 100
    val txFee = Parameters.MinFee
    val metadataValue = Parameters.OneErg
    val totalValue = (numSubpools * metadataValue) + (txFee * 2)
    val initialBox = buildUserBox(totalValue, 0)
    val prover = dummyProver

    ergoClient.execute {
      ctx =>
        val createSubpoolTokenTx = new CreateSubpoolTokenTx(ctx.newTxBuilder())
        val uTx = createSubpoolTokenTx
          .numSubpools(numSubpools)
          .txFee(txFee)
          .metadataValue(metadataValue)
          .creatorAddress(creatorAddress)
          .inputBoxes(Seq(initialBox))
          .build()

        var signedTx: SignedTransaction = null

        try {
          signedTx = prover.sign(uTx)
        }catch {
          case e: Exception =>
            fail("Transaction could not be signed!", e)
        }

        assert(signedTx.getOutputsToSpend.get(0).getValue == totalValue - txFee, "Token Output box has incorrect value")

    }
  }

  test("Token Output Box has correct number of tokens") {

    val numSubpools = 45
    val txFee = Parameters.MinFee
    val metadataValue = Parameters.OneErg
    val totalValue = (numSubpools * metadataValue) + (txFee * 2)
    val initialBox = buildUserBox(totalValue, 0)
    val prover = dummyProver

    ergoClient.execute {
      ctx =>
        val createSubpoolTokenTx = new CreateSubpoolTokenTx(ctx.newTxBuilder())
        val uTx = createSubpoolTokenTx
          .numSubpools(numSubpools)
          .txFee(txFee)
          .metadataValue(metadataValue)
          .creatorAddress(creatorAddress)
          .inputBoxes(Seq(initialBox))
          .build()

        var signedTx: SignedTransaction = null

        try {
          signedTx = prover.sign(uTx)
        }catch {
          case e: Exception =>
            fail("Transaction could not be signed!", e)
        }
        assert(signedTx.getOutputsToSpend.get(0).getTokens.get(0).getValue == numSubpools, "Token Output box has incorrect number of tokens")
    }
  }

  test("Token Output Box has correct token id") {

    val numSubpools = 88
    val txFee = Parameters.MinFee
    val metadataValue = Parameters.OneErg
    val totalValue = (numSubpools * metadataValue) + (txFee * 2)
    val initialBox = buildUserBox(totalValue, 0)

    val prover = dummyProver

    ergoClient.execute {
      ctx =>
        val createSubpoolTokenTx = new CreateSubpoolTokenTx(ctx.newTxBuilder())
        val uTx = createSubpoolTokenTx
          .numSubpools(numSubpools)
          .txFee(txFee)
          .metadataValue(metadataValue)
          .creatorAddress(creatorAddress)
          .inputBoxes(Seq(initialBox))
          .build()

        var signedTx: SignedTransaction = null

        try {
          signedTx = prover.sign(uTx)
        }catch {
          case e: Exception =>
            fail("Transaction could not be signed!", e)
        }
        assert(signedTx.getOutputsToSpend.get(0).getTokens.get(0).getId == initialBox.getId, "Token Output box has incorrect token id")
    }
  }

  test("Correct number of metadata boxes generated without change") {

    val numSubpools = 100
    val txFee = Parameters.MinFee
    val metadataValue = Parameters.OneErg
    val totalValue = (numSubpools * metadataValue) + (txFee * 2)
    val initialBox = buildUserBox(totalValue, 0)
    val prover = dummyProver

    ergoClient.execute {
      ctx =>
        val createSubpoolTokenTx = new CreateSubpoolTokenTx(ctx.newTxBuilder())
        val uTx = createSubpoolTokenTx
          .numSubpools(numSubpools)
          .txFee(txFee)
          .metadataValue(metadataValue)
          .creatorAddress(creatorAddress)
          .inputBoxes(Seq(initialBox))
          .build()

        var signedTx: SignedTransaction = null

        try {
          signedTx = prover.sign(uTx)
        }catch {
          case e: Exception =>
            fail("Subpool Token Tx could not be signed!", e)
        }

        val metadataContract = MetadataContract.generateMetadataContract(ctx)

        val generateMultipleTx = new GenerateMultipleTx(ctx.newTxBuilder())
        val unsignedGenTx = generateMultipleTx
          .txFee(txFee)
          .creatorAddress(creatorAddress)
          .metadataValue(metadataValue)
          .metadataContract(metadataContract)
          .tokenInputBox(signedTx.getOutputsToSpend.get(0))
          .smartPoolToken(signedTx.getOutputsToSpend.get(0).getTokens.get(0))
          .build()

        var signedGenTx: SignedTransaction = null
        try {
          signedGenTx = prover.sign(unsignedGenTx)
        }catch {
          case e: Exception =>
            fail("GenerateMultiple Tx could not be signed!")
        }

        assert(signedGenTx.getOutputsToSpend.size() == numSubpools + 1)
      }
  }

  test("All metadata boxes have correct token") {

    val numSubpools = 100
    val txFee = Parameters.MinFee
    val metadataValue = Parameters.OneErg
    val totalValue = (numSubpools * metadataValue) + (txFee * 2)
    val initialBox = buildUserBox(totalValue, 0)
    val prover = dummyProver

    ergoClient.execute {
      ctx =>
        val createSubpoolTokenTx = new CreateSubpoolTokenTx(ctx.newTxBuilder())
        val uTx = createSubpoolTokenTx
          .numSubpools(numSubpools)
          .txFee(txFee)
          .metadataValue(metadataValue)
          .creatorAddress(creatorAddress)
          .inputBoxes(Seq(initialBox))
          .build()

        var signedTx: SignedTransaction = null

        try {
          signedTx = prover.sign(uTx)
        }catch {
          case e: Exception =>
            fail("Subpool Token Tx could not be signed!", e)
        }

        val metadataContract = MetadataContract.generateMetadataContract(ctx)

        val generateMultipleTx = new GenerateMultipleTx(ctx.newTxBuilder())
        val unsignedGenTx = generateMultipleTx
          .txFee(txFee)
          .creatorAddress(creatorAddress)
          .metadataValue(metadataValue)
          .metadataContract(metadataContract)
          .tokenInputBox(signedTx.getOutputsToSpend.get(0))
          .smartPoolToken(signedTx.getOutputsToSpend.get(0).getTokens.get(0))
          .build()

        var signedGenTx: SignedTransaction = null
        try {
          signedGenTx = prover.sign(unsignedGenTx)
        }catch {
          case e: Exception =>
            fail("GenerateMultiple Tx could not be signed!")
        }

        val assertion = signedGenTx.getOutputsToSpend.asScala
          .filter(b => b.getErgoTree.bytes sameElements metadataContract.getErgoTree.bytes)
          .forall(b => b.getTokens.size() == 1 && b.getTokens.get(0).getId == initialBox.getId && b.getTokens.get(0).getValue == 1)
        assert(assertion)
    }
  }

  test("All metadata boxes have correct subpool nums") {

    val numSubpools = 100
    val txFee = Parameters.MinFee
    val metadataValue = Parameters.OneErg
    val totalValue = (numSubpools * metadataValue) + (txFee * 2)
    val initialBox = buildUserBox(totalValue, 0)
    val prover = dummyProver

    ergoClient.execute {
      ctx =>
        val createSubpoolTokenTx = new CreateSubpoolTokenTx(ctx.newTxBuilder())
        val uTx = createSubpoolTokenTx
          .numSubpools(numSubpools)
          .txFee(txFee)
          .metadataValue(metadataValue)
          .creatorAddress(creatorAddress)
          .inputBoxes(Seq(initialBox))
          .build()

        var signedTx: SignedTransaction = null

        try {
          signedTx = prover.sign(uTx)
        }catch {
          case e: Exception =>
            fail("Subpool Token Tx could not be signed!", e)
        }

        val metadataContract = MetadataContract.generateMetadataContract(ctx)

        val generateMultipleTx = new GenerateMultipleTx(ctx.newTxBuilder())
        val unsignedGenTx = generateMultipleTx
          .txFee(txFee)
          .creatorAddress(creatorAddress)
          .metadataValue(metadataValue)
          .metadataContract(metadataContract)
          .tokenInputBox(signedTx.getOutputsToSpend.get(0))
          .smartPoolToken(signedTx.getOutputsToSpend.get(0).getTokens.get(0))
          .build()

        var signedGenTx: SignedTransaction = null
        try {
          signedGenTx = prover.sign(unsignedGenTx)
        }catch {
          case e: Exception =>
            fail("GenerateMultiple Tx could not be signed!")
        }

        val metadataBoxes = signedGenTx.getOutputsToSpend.asScala
          .filter(b => b.getErgoTree.bytes sameElements metadataContract.getErgoTree.bytes)
          .map(b => new MetadataInputBox(b, initialBox.getId))
          .toArray
        val assertion = for(i <- 0 until 100) yield metadataBoxes.exists(b => b.poolInfo.getSubpool == i.toLong)
        val assertionReduced = assertion.reduce{
          (b1, b2) => b1 && b2
        }
        assert(assertionReduced)
        // println("Current metadata registers: ")
        // metadataBoxes.foreach(b => println(b.metadataRegisters))
    }
  }

}
