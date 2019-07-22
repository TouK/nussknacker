package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import pl.touk.http.argonaut.{Argonaut62Support, JsonMarshaller}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class UserResources(implicit ec: ExecutionContext, jsonMarshaller: JsonMarshaller)
  extends Directives with Argonaut62Support with RouteWithUser {

  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.ui.codec.UiCodecs._
  def route(implicit user: LoggedUser): Route =
    path("user") {
      get {
        complete {
          DisplayableUser(
            id = user.id,
            permissions = user.categoryPermissions.values.flatten.map(_.toString).toList.distinct,
            categories = user.categoryPermissions.keys.toList,
            categoryPermissions = user.categoryPermissions.mapValues(_.map(_.toString)))
        }
      }
    }

}

case class DisplayableUser(id: String, permissions: List[String], categories: List[String],categoryPermissions: Map[String, Set[String]])