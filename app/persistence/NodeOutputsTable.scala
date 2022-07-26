package persistence

import models.DatabaseModels.{NodeInput, NodeOutput}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

class NodeOutputsTable(tag: Tag) extends Table[NodeOutput](tag, "node_inputs") {
  def boxId         = column[String]("box_id", O.PrimaryKey)
  def txId          = column[String]("tx_id", O.PrimaryKey)
  def headerId      = column[String]("header_id", O.PrimaryKey)
  def value         = column[Long]("value")
  def address       = column[String]("address")
  def *            = (boxId, txId, headerId, value, address) <> (NodeOutput.tupled, NodeOutput.unapply)
}


