package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.process.ProcessTypesForCategories
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class UserResources(typesForCategories: ProcessTypesForCategories)(implicit ec: ExecutionContext) extends Directives with FailFastCirceSupport with RouteWithUser {
  def route(implicit user: LoggedUser): Route =
    path("user") {
      get {
        complete {
          DisplayableUser(
            id = user.id,
            isAdmin = user.isAdmin,
            categories = typesForCategories.getAllCategories,
            categoryPermissions = user.categoryPermissions.mapValues(_.map(_.toString)))
        }
      }
    }
}

@JsonCodec case class DisplayableUser(id: String, isAdmin: Boolean, categories: List[String], categoryPermissions: Map[String, Set[String]])