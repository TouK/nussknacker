package pl.touk.esp.ui.api

import akka.http.scaladsl.server.Directives
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.ExecutionContext

class AppResources(buildInfo: Map[String, String])(implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support {

  import argonaut.ArgonautShapeless._

  val route = (user: LoggedUser) =>
    pathPrefix("app") {
      path("buildInfo") {
        get {
          complete {
            buildInfo
          }
        }
      }
    }
}
