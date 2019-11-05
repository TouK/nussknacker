package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.RulesSet
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ClientApi.{DefaultAccessTokenResponse, DefaultProfileResponse}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DefaultOAuth2Service(clientApi: OAuth2ClientApi[DefaultProfileResponse, DefaultAccessTokenResponse], configuration: OAuth2Configuration, allCategories: List[String]) extends OAuth2Service with LazyLogging {
  override def authenticate(code: String): Future[OAuth2AuthenticateData] = {
    clientApi.accessTokenRequest(code).map { resp =>
      OAuth2AuthenticateData(
        access_token = resp.access_token,
        token_type = resp.token_type,
        refresh_token = resp.refresh_token
      )
    }
  }

  override def authorize(token: String): Future[LoggedUser] = {
    clientApi.profileRequest(token).map { profile =>
      val userRoles = DefaultOAuth2ServiceFactory.getUserRoles(profile.email, configuration)

      val rulesSet = RulesSet.getOnlyMatchingRules(userRoles, configuration.rules, allCategories)
      if (rulesSet.isAdmin) {
        LoggedUser(id = profile.id.toString, isAdmin = true)
      } else {
        LoggedUser(
          id = profile.id.toString,
          categoryPermissions = rulesSet.permissions,
          globalPermissions = rulesSet.globalPermissions
        )
      }
    }
  }
}

class DefaultOAuth2ServiceFactory extends OAuth2ServiceFactory {
  override def create(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service =
    DefaultOAuth2ServiceFactory.defaultService(configuration, allCategories)
}

object DefaultOAuth2ServiceFactory extends {
  val defaultUserRole = "User"

  def apply(): DefaultOAuth2ServiceFactory = new DefaultOAuth2ServiceFactory()

  def defaultService(configuration: OAuth2Configuration, allCategories: List[String]): DefaultOAuth2Service =
    new DefaultOAuth2Service(OAuth2ClientApi[DefaultProfileResponse, DefaultAccessTokenResponse](configuration), configuration, allCategories)

  def service(configuration: OAuth2Configuration, allCategories: List[String])(implicit backend: SttpBackend[Future, Nothing, NothingT]): DefaultOAuth2Service =
    new DefaultOAuth2Service(new OAuth2ClientApi[DefaultProfileResponse, DefaultAccessTokenResponse](configuration), configuration, allCategories)

  def getUserRoles(email: String, configuration: OAuth2Configuration, defaults: List[String] = List(defaultUserRole)): List[String] =
    configuration.users.find(_.email.equals(email)).map(_.roles ++ defaults).getOrElse(defaults)
}