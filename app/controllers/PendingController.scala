package io.getblok.subpooling
package controllers

import play.api.Configuration
import play.api.mvc.ControllerComponents

import javax.inject.{Inject, Singleton}

@Singleton
class PendingController @Inject()(val components: ControllerComponents, config: Configuration)
extends SubpoolBaseController(components, config){


}
