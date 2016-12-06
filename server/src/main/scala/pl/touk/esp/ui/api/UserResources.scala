package pl.touk.esp.ui.api

import akka.http.scaladsl.server.Directives
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.ExecutionContext

class UserResources(implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support {

  import argonaut.ArgonautShapeless._
  import pl.touk.esp.ui.codec.UiCodecs._

  val route = (user:LoggedUser) =>
    path("user") {
      get {
        complete {
          DisplayableUser(user.id, user.permissions.map(_.toString), user.categories)
        }
      }
    }

}

case class DisplayableUser(id: String, permissions: List[String], categories: List[String])