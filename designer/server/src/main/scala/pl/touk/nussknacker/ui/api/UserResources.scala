package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.process.{ProcessCategoryService, UserCategoryService}
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser}

import scala.concurrent.ExecutionContext

class UserResources(getProcessCategoryService: () => ProcessCategoryService)(implicit ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with RouteWithUser {

  def securedRoute(implicit user: LoggedUser): Route =
    path("user") {
      get {
        complete {
          val allUserAccessibleCategories = new UserCategoryService(getProcessCategoryService()).getUserCategories(user)
          DisplayableUser(user, allUserAccessibleCategories)
        }
      }
    }

}

@JsonCodec final case class DisplayableUser private (
    id: String,
    username: String,
    isAdmin: Boolean,
    categories: List[String],
    categoryPermissions: Map[String, List[String]],
    globalPermissions: List[GlobalPermission]
)

object DisplayableUser {
  import pl.touk.nussknacker.engine.util.Implicits._

  def apply(user: LoggedUser, allUserAccessibleCategories: List[String]): DisplayableUser = user match {
    case CommonUser(id, username, categoryPermissions, globalPermissions) =>
      new DisplayableUser(
        id = id,
        isAdmin = false,
        username = username,
        categories = allUserAccessibleCategories,
        categoryPermissions = categoryPermissions.mapValuesNow(_.map(_.toString).toList.sorted),
        globalPermissions = globalPermissions
      )
    case AdminUser(id, username) =>
      new DisplayableUser(
        id = id,
        isAdmin = true,
        username = username,
        categories = allUserAccessibleCategories,
        categoryPermissions = Map.empty,
        globalPermissions = Nil
      )
  }

}
