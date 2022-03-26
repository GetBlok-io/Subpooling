package app.services

import app.config.DistributeBlocksConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class DistributeBlocks @Autowired()(){
  def getMessage: String = {
    "Hello2"
  }
}
