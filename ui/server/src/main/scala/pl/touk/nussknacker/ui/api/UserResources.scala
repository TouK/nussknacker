package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.process.ProcessTypesForCategories
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser, Permission}

import scala.concurrent.ExecutionContext

class UserResources(typesForCategories: ProcessTypesForCategories)(implicit ec: ExecutionContext) extends Directives with FailFastCirceSupport with RouteWithUser {
  def route(implicit user: LoggedUser): Route =
    path("user") {
      get {
        complete {
          DisplayableUser(user, typesForCategories.getAllCategories)
        }
      }
    }
}

@JsonCodec case class DisplayableUser private(id: String, isAdmin: Boolean, categories: List[String], categoryPermissions: Map[String, Set[String]])

object DisplayableUser {
  import pl.touk.nussknacker.engine.util.Implicits._

  def apply(user: LoggedUser, allCategories: List[String]): DisplayableUser = user match {
    case CommonUser(id, categoryPermissions) => new DisplayableUser(
      id = id,
      isAdmin = false,
      categories = allCategories,
      categoryPermissions = categoryPermissions.mapValuesNow(_.map(_.toString))
    )
    case AdminUser(id) => new DisplayableUser(
      id = id,
      isAdmin = true,
      categories = allCategories,
      categoryPermissions = allCategories.map(category => category -> Permission.ALL_PERMISSIONS.map(_.toString)).toMap
    )
  }
}