package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ClientApi.DefaultAccessTokenResponse
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class DefaultOAuth2Service[ProfileResponse: Decoder](clientApi: OAuth2ClientApi[ProfileResponse, DefaultAccessTokenResponse],
                                                     oAuth2Profile: OAuth2Profile[ProfileResponse],
                                                     configuration: OAuth2Configuration,
                                                     allCategories: List[String]) extends OAuth2Service with LazyLogging {
  def authenticate(code: String): Future[OAuth2AuthenticateData] = {
    clientApi.accessTokenRequest(code).map { resp =>
      OAuth2AuthenticateData(
        access_token = resp.access_token,
        token_type = resp.token_type,
        refresh_token = resp.refresh_token
      )
    }
  }

  def authorize(token: String): Future[LoggedUser] = clientApi.profileRequest(token).map(oAuth2Profile.getLoggedUser(_, configuration, allCategories))
}

class DefaultOAuth2ServiceFactoryWithProfileFormat[ProfileResponse: Decoder](oAuth2Profile: OAuth2Profile[ProfileResponse]) {
  def defaultService(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service =
    new DefaultOAuth2Service[ProfileResponse](OAuth2ClientApi[ProfileResponse, DefaultAccessTokenResponse](configuration), oAuth2Profile, configuration, allCategories)

  def service(configuration: OAuth2Configuration, allCategories: List[String])(implicit backend: SttpBackend[Future, Nothing, NothingT], ec: ExecutionContext): OAuth2Service =
    new DefaultOAuth2Service[ProfileResponse](new OAuth2ClientApi[ProfileResponse, DefaultAccessTokenResponse](configuration), oAuth2Profile, configuration, allCategories)
}

object DefaultOAuth2ServiceFactoryWithProfileFormat {
  def apply(configuration: OAuth2Configuration, allCategories: List[String]) = {
    configuration.profileFormat.getOrElse {
      throw new Exception("profileFormat is missing in the authentication configuration")
    } match {
      case ProfileFormat.GITHUB => new DefaultOAuth2ServiceFactoryWithProfileFormat[GitHubProfileResponse](GitHubProfile)
      case ProfileFormat.AUTH0 => new DefaultOAuth2ServiceFactoryWithProfileFormat[Auth0ProfileResponse](Auth0Profile)
    }
  }
}

class DefaultOAuth2ServiceFactory extends OAuth2ServiceFactory {
  def create(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service =
    DefaultOAuth2ServiceFactory.defaultService(configuration, allCategories)
}

object DefaultOAuth2ServiceFactory extends {
  def apply(): DefaultOAuth2ServiceFactory = new DefaultOAuth2ServiceFactory

  def defaultService(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service =
    DefaultOAuth2ServiceFactoryWithProfileFormat(configuration, allCategories).defaultService(configuration, allCategories)

  def service(configuration: OAuth2Configuration, allCategories: List[String])(implicit backend: SttpBackend[Future, Nothing, NothingT]): OAuth2Service =
    DefaultOAuth2ServiceFactoryWithProfileFormat(configuration, allCategories).service(configuration, allCategories)
}
