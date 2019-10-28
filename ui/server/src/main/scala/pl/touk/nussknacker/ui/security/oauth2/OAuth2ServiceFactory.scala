package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ClientApi.{DefaultAccessTokenResponse, DefaultProfileResponse}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ServiceFactory.{OAuth2AuthenticateData, OAuth2Profile}
import pl.touk.nussknacker.ui.util.ClassLoaderUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OAuth2Service {
  def authenticate(code: String): Future[OAuth2AuthenticateData]
  def profile(token: String): Future[OAuth2Profile]
}

class DefaultOAuth2Service(clientApi: OAuth2ClientApi[DefaultProfileResponse, DefaultAccessTokenResponse], configuration: OAuth2Configuration) extends OAuth2Service with LazyLogging {
  private val defaultUserRole = "User"

  override def authenticate(code: String): Future[OAuth2AuthenticateData] = {
    clientApi.accessTokenRequest(code).map{ resp =>
      OAuth2AuthenticateData(
        access_token = resp.access_token,
        token_type = resp.token_type,
        refresh_token = resp.refresh_token
      )
    }
  }

  override def profile(token: String): Future[OAuth2Profile] = {
    clientApi.profileRequest(token).map{ prf =>
      OAuth2Profile(
        id = prf.id.toString,
        email = prf.email,
        roles = OAuth2ServiceFactory.getUserRoles(prf.email, configuration, List.apply(defaultUserRole))
      )
    }
  }
}

object OAuth2ServiceFactory {
  def apply(configuration: OAuth2Configuration, classLoader: ClassLoader): OAuth2Service = ClassLoaderUtils[OAuth2Service](classLoader).loadClass {
    val clientApi = OAuth2ClientApi[DefaultProfileResponse, DefaultAccessTokenResponse](configuration)
    new DefaultOAuth2Service(clientApi, configuration)
  }

  def getUserRoles(email: String, configuration: OAuth2Configuration, defaults: List[String] = List.empty): List[String] =
    configuration.users.find(_.email.equals(email)).map(_.roles).getOrElse(defaults)

  final case class OAuth2AuthenticateData(access_token: String, token_type: String, refresh_token: Option[String])

  final case class OAuth2Profile(id: String, email: String, roles: List[String])
}