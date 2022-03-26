package app.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

import scala.beans.BeanProperty

@Component
@ConfigurationProperties("distribute-blocks")
case class DistributeBlocksConfig() {
  @BeanProperty
  var myKey: String = _
}
