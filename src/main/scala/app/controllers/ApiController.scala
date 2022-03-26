package app.controllers

import app.services.DistributeBlocks
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{RequestMapping, RequestMethod, ResponseBody}


@Controller
class ApiController @Autowired()(distBlocks: DistributeBlocks) {
  @RequestMapping(path = Array("/test"), method = Array(RequestMethod.GET), produces = Array(MediaType.TEXT_PLAIN_VALUE))
  @ResponseBody
  def handleRequest(): String = {
    "Hallo " + distBlocks.getMessage
  }
}
