package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, GlobalPermission, LoggedUser, Permission}

import scala.concurrent.ExecutionContext

class UserResources(processCategoryService: ProcessCategoryService)(implicit ec: ExecutionContext) extends Directives with FailFastCirceSupport with RouteWithUser {
  def securedRoute(implicit user: LoggedUser): Route =
    path("user") {
      get {
        complete {
          DisplayableUser(user, processCategoryService.getAllCategories)
        }
      }
    }
}

@JsonCodec case class DisplayableUser private(id: String,
                                              username: String,
                                              isAdmin: Boolean,
                                              categories: List[String],
                                              categoryPermissions: Map[String, List[String]],
                                              globalPermissions: List[GlobalPermission])

object DisplayableUser {
  import pl.touk.nussknacker.engine.util.Implicits._

  def apply(user: LoggedUser, allCategories: List[String]): DisplayableUser = user match {
    case CommonUser(id, username, categoryPermissions, globalPermissions) => new DisplayableUser(
      id = id,
      isAdmin = false,
      username = username,
      categories = allCategories.sorted,
      categoryPermissions = categoryPermissions.mapValuesNow(_.map(_.toString).toList.sorted),
      globalPermissions = globalPermissions
    )
    case AdminUser(id, username) => new DisplayableUser(
      id = id,
      isAdmin = true,
      username = username,
      categories = allCategories.sorted,
      categoryPermissions = Map.empty,
      globalPermissions = Nil
    )
  }
}