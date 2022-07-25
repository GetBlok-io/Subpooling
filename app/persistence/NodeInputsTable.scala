package persistence

import models.DatabaseModels.{NodeAsset, NodeInput}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

class NodeInputsTable(tag: Tag) extends Table[NodeInput](tag, "node_assets") {
  def boxId         = column[String]("box_id", O.PrimaryKey)
  def txId          = column[String]("tx_id", O.PrimaryKey)
  def headerId      = column[String]("header_id", O.PrimaryKey)
  def proofBytes    = column[Option[String]]("proof_bytes")
  def extension     = column[String]("extension")
  def index         = column[Int]("index")
  def mainChain     = column[Boolean]("main_chain")
  def *            = (boxId, txId, headerId, proofBytes, extension, index, mainChain) <> (NodeInput.tupled, NodeInput.unapply)
}


