package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.process.ProcessTypesForCategories
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, GlobalPermission, LoggedUser, Permission}

import scala.concurrent.ExecutionContext

class UserResources(typesForCategories: ProcessTypesForCategories)(implicit ec: ExecutionContext) extends Directives with FailFastCirceSupport with RouteWithUser {
  def securedRoute(implicit user: LoggedUser): Route =
    path("user") {
      get {
        complete {
          DisplayableUser(user, typesForCategories.getAllCategories)
        }
      }
    }
}

@JsonCodec case class GlobalPermissions private(adminTab: Boolean)

object GlobalPermissions {
  val ALL = apply(GlobalPermission.ALL_PERMISSIONS.toList)

  def apply(permissions: List[GlobalPermission]): GlobalPermissions = {
    permissions.foldLeft(GlobalPermissions(false)) {
      case (acc, GlobalPermission.AdminTab) => acc.copy(adminTab = true)
      case (acc, _) => acc
    }
  }
}

@JsonCodec case class DisplayableUser private(id: String,
                                              username: String,
                                              isAdmin: Boolean,
                                              categories: List[String],
                                              categoryPermissions: Map[String, List[String]],
                                              globalPermissions: GlobalPermissions)

object DisplayableUser {
  import pl.touk.nussknacker.engine.util.Implicits._

  def apply(user: LoggedUser, allCategories: List[String]): DisplayableUser = user match {
    case CommonUser(id, username, categoryPermissions, globalPermissions) => new DisplayableUser(
      id = id,
      isAdmin = false,
      username = username,
      categories = allCategories.sorted,
      categoryPermissions = categoryPermissions.mapValuesNow(_.map(_.toString).toList.sorted),
      globalPermissions = GlobalPermissions(globalPermissions)
    )
    case AdminUser(id, username) => new DisplayableUser(
      id = id,
      isAdmin = true,
      username = username,
      categories = allCategories.sorted,
      categoryPermissions = allCategories.map(category => category -> Permission.ALL_PERMISSIONS.map(_.toString).toList.sorted).toMap,
      globalPermissions = GlobalPermissions.ALL
    )
  }
}