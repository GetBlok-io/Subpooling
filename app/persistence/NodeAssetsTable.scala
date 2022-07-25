package persistence

import models.DatabaseModels.{Balance, NodeAsset}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.LocalDateTime

class NodeAssetsTable(tag: Tag) extends Table[NodeAsset](tag, "node_assets") {
  def tokenId       = column[String]("token_id", O.PrimaryKey)
  def boxId         = column[String]("box_id", O.PrimaryKey)
  def headerId      = column[String]("header_id", O.PrimaryKey)
  def index         = column[Int]("index")
  def value         = column[Long]("value")
  def *            = (tokenId, boxId, headerId, index, value) <> (NodeAsset.tupled, NodeAsset.unapply)
}


