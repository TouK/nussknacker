package pl.touk.esp.ui.api

import akka.http.scaladsl.server.Directives
import argonaut.{EncodeJson, Json, PrettyParams}
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.ExecutionContext

class UserResources(implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support {

  import argonaut.ArgonautShapeless._

  implicit val printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty

  implicit val userEncodeEncode = EncodeJson.of[DisplayableUser]

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