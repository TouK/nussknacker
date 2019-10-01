package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.security.api.LoggedUser
import io.circe._, io.circe.generic.semiauto._

import scala.concurrent.ExecutionContext

class UserResources(implicit ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with RouteWithUser {

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
@JsonCodec case class DisplayableUser(id: String, permissions: List[String], categories: List[String],categoryPermissions: Map[String, Set[String]])