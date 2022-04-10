package controllers

import configs._

import play.api.Configuration
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}

import javax.inject.{Inject, Singleton}
@Singleton
class RESTController @Inject()(val controllerComponents: ControllerComponents, config: Configuration)
extends BaseController{

  def getHelp(): Action[AnyContent] = Action {

    NoContent
  }

}
