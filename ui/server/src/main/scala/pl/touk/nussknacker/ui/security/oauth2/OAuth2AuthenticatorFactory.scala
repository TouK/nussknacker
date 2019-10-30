package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.AuthenticationConfigurationFactory
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.LoggedUserAuth
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.api.{AuthenticatorFactory, GlobalPermission, Permission}

class OAuth2AuthenticatorFactory extends AuthenticatorFactory with LazyLogging {

  override def createAuthenticator(config: Config, classLoader: ClassLoader): LoggedUserAuth = {
    val configuration = AuthenticationConfigurationFactory.oAuth2Config(config)
    val service = OAuth2ServiceFactory(configuration, classLoader)
    createAuthenticator(configuration, service)
  }

  def createAuthenticator(config: OAuth2Configuration, service: OAuth2Service): LoggedUserAuth = {
    SecurityDirectives.authenticateOAuth2Async(
      authenticator = OAuth2Authenticator(config, service),
      realm = realm
    )
  }
}

object OAuth2AuthenticatorFactory {
  import cats.instances.all._
  import cats.syntax.semigroup._

  def isAdmin(roles: List[String], rules: List[OAuth2ConfigRule]): Boolean =
    getOnlyMatchingRules(roles, rules).exists(rule => rule.isAdmin)

  def getOnlyMatchingRules(roles: List[String], rules: List[OAuth2ConfigRule]): List[OAuth2ConfigRule] =
    rules.filter(rule => roles.contains(rule.roleName))

  def getPermissions(roles: List[String], rules: List[OAuth2ConfigRule]): Map[String, Set[Permission]] = {
    val isAdminUser = isAdmin(roles, rules)
    getOnlyMatchingRules(roles, rules)
      .flatMap { rule =>
        rule.categories.map(_ -> (if (isAdminUser) Permission.ALL_PERMISSIONS else rule.permissions.toSet))
      }.map(List(_).toMap)
      .foldLeft(Map.empty[String, Set[Permission]])(_ |+| _)
  }

  def getGlobalPermissions(roles: List[String], rules: List[OAuth2ConfigRule]): List[GlobalPermission] = {
    if (isAdmin(roles, rules)) {
      GlobalPermission.ALL_PERMISSIONS.toList
    } else {
      getOnlyMatchingRules(roles, rules)
        .flatMap(_.globalPermissions)
        .distinct
    }
  }

  def apply(): OAuth2AuthenticatorFactory = new OAuth2AuthenticatorFactory()
}
