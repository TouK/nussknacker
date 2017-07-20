package pl.touk.esp.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.http.argonaut.Argonaut62Support

import scala.concurrent.ExecutionContext

class UserResources(implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support with RouteWithUser {

  import argonaut.ArgonautShapeless._
  import pl.touk.esp.ui.codec.UiCodecs._

  def route(implicit user: LoggedUser): Route =
    path("user") {
      get {
        complete {
          DisplayableUser(user.id, user.permissions.map(_.toString), user.categories)
        }
      }
    }

}

case class DisplayableUser(id: String, permissions: List[String], categories: List[String])