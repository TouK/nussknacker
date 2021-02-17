package pl.touk.nussknacker.ui.security.oauth2

import io.circe.Decoder
import io.circe.generic.JsonCodec
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future

@JsonCodec case class DefaultJwtAccessToken
(
  iss: Option[String],
  sub: Option[String],
  aud: Option[String],
  exp: Option[Long],
  nbf: Option[Long],
  iat: Option[Long],
  jti: Option[String]
) extends JwtStandardClaims

@JsonCodec case class DefaultAccessTokenResponse
(
  access_token: String,
  token_type: String,
  refresh_token: Option[String],
  expires_in: Option[Long] = None,
  id_token: Option[String] = None
) extends OpenIdConnectAccessTokenResponse

class DefaultOAuth2ServiceFactoryWithProfileFormat[ProfileResponse : Decoder](oAuth2Profile: OAuth2Profile[ProfileResponse]) {
  import scala.concurrent.ExecutionContext.Implicits.global

  type DefaultOAuth2Service[T] = OpenIdConnectService[ProfileResponse, DefaultAccessTokenResponse, DefaultJwtAccessToken]

  def defaultService(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service =
    new DefaultOAuth2Service[ProfileResponse](OAuth2ClientApi[ProfileResponse, DefaultAccessTokenResponse](configuration), oAuth2Profile.getLoggedUser(_, configuration, allCategories), configuration)

  def service(configuration: OAuth2Configuration, allCategories: List[String])(implicit backend: SttpBackend[Future, Nothing, NothingT]): OAuth2Service =
    new DefaultOAuth2Service[ProfileResponse](new OAuth2ClientApi[ProfileResponse, DefaultAccessTokenResponse](configuration), oAuth2Profile.getLoggedUser(_, configuration, allCategories), configuration)
}

object DefaultOAuth2ServiceFactoryWithProfileFormat {
  def apply(configuration: OAuth2Configuration, allCategories: List[String]) = {
    configuration.profileFormat.getOrElse {
      throw new Exception("profileFormat is missing in the authentication configuration")
    } match {
      case ProfileFormat.GITHUB => new DefaultOAuth2ServiceFactoryWithProfileFormat[GitHubProfileResponse](GitHubProfile)
      case ProfileFormat.DEFAULT => new DefaultOAuth2ServiceFactoryWithProfileFormat[DefaultProfileResponse](DefaultProfile)
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
