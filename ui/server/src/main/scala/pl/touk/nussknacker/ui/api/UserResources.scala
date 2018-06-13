package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class UserResources(implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support with RouteWithUser {

  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.ui.codec.UiCodecs._

  def route(implicit user: LoggedUser): Route =
    path("user") {
      get {
        complete {
          //FIXME: implement catergories permissions hierarchy in frontend
          DisplayableUser(user.id, user.categoryPermissions.values.flatten.map(_.toString).toList, user.categoryPermissions.keys.toList)
        }
      }
    }

}

case class DisplayableUser(id: String, permissions: List[String], categories: List[String])