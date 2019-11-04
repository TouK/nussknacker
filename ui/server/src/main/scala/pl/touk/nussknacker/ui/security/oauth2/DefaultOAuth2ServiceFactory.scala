package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ClientApi.{DefaultAccessTokenResponse, DefaultProfileResponse}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ServiceProvider.{OAuth2AuthenticateData}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class DefaultOAuth2Service(clientApi: OAuth2ClientApi[DefaultProfileResponse, DefaultAccessTokenResponse], configuration: OAuth2Configuration) extends OAuth2Service with LazyLogging {
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
      val roles = OAuth2ServiceFactory.getOnlyMatchingRoles(userRoles, configuration.rules)
      val isAdmin = OAuth2ServiceFactory.isAdmin(roles)

      if (isAdmin) {
        LoggedUser(id = profile.id.toString, isAdmin = true)
      } else {
        LoggedUser(
          id = profile.id.toString,
          categoryPermissions = OAuth2ServiceFactory.getPermissions(roles),
          globalPermissions = OAuth2ServiceFactory.getGlobalPermissions(roles)
        )
      }
    }
  }
}

class DefaultOAuth2ServiceFactory extends OAuth2ServiceFactory {
  override def create(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service =
    DefaultOAuth2ServiceFactory.defaultService(configuration)
}

object DefaultOAuth2ServiceFactory extends {
  val defaultUserRole = "User"

  def apply(): DefaultOAuth2ServiceFactory = new DefaultOAuth2ServiceFactory()

  def defaultService(configuration: OAuth2Configuration): DefaultOAuth2Service =
    new DefaultOAuth2Service(OAuth2ClientApi[DefaultProfileResponse, DefaultAccessTokenResponse](configuration), configuration)

  def service(configuration: OAuth2Configuration)(implicit backend: SttpBackend[Future, Nothing, NothingT]): DefaultOAuth2Service =
    new DefaultOAuth2Service(new OAuth2ClientApi[DefaultProfileResponse, DefaultAccessTokenResponse](configuration), configuration)

  def getUserRoles(email: String, configuration: OAuth2Configuration, defaults: List[String] = List.apply(defaultUserRole)): List[String] =
    configuration.users.find(_.email.equals(email)).map(_.roles ++ defaults).getOrElse(defaults)
}