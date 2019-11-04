package pl.touk.nussknacker.ui.security.oauth2

import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.{GlobalPermission, LoggedUser, Permission}
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Configuration.OAuth2ConfigRule
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ServiceProvider.OAuth2AuthenticateData

import scala.concurrent.Future

trait OAuth2Service {
  def authenticate(code: String): Future[OAuth2AuthenticateData]
  def authorize(token: String): Future[LoggedUser]
}

trait OAuth2ServiceFactory {
  def create(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service
}

object OAuth2ServiceFactory {
  import cats.instances.all._
  import cats.syntax.semigroup._

  def getPermissions(roles: List[OAuth2ConfigRule]): Map[String, Set[Permission]] = {
    val isAdminUser = isAdmin(roles)

    roles.flatMap { rule =>
      rule.categories.map(_ -> (if (isAdminUser) Permission.ALL_PERMISSIONS else rule.permissions.toSet))
    }.map(List(_).toMap)
      .foldLeft(Map.empty[String, Set[Permission]])(_ |+| _)
  }

  def getGlobalPermissions(roles: List[OAuth2ConfigRule]): List[GlobalPermission] = {
    if (isAdmin(roles)) {
      GlobalPermission.ALL_PERMISSIONS.toList
    } else {
      roles.flatMap(_.globalPermissions).distinct
    }
  }

  def getOnlyMatchingRoles(roles: List[String], rules: List[OAuth2ConfigRule]): List[OAuth2ConfigRule] =
    rules.filter(rule => roles.contains(rule.role))

  private[oauth2] def isAdmin(roles: List[OAuth2ConfigRule]): Boolean =
    roles.exists(rule => rule.isAdmin)
}
