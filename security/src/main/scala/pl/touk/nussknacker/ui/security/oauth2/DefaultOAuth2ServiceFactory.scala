package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.api.AuthenticatedUser
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class DefaultOAuth2ServiceFactory extends OAuth2ServiceFactory with LazyLogging {

  override def create(configuration: OAuth2Configuration)(
      implicit ec: ExecutionContext,
      backend: SttpBackend[Future, Any]
  ): OAuth2Service[AuthenticatedUser, OAuth2AuthorizationData] = {
    new CachingOAuth2Service(
      configuration.profileFormat.getOrElse {
        throw new Exception("profileFormat is missing in the authentication configuration")
      } match {
        case ProfileFormat.OIDC =>
          logger.warn(
            "Switch to the OIDC authentication provider instead of the OIDC format in the generic OAuth2 provider"
          )
          new UserMappingOAuth2Service[OpenIdConnectUserInfo, DefaultOidcAuthorizationData](
            GenericOidcService(configuration),
            new OpenIdConnectProfileAuthentication(configuration)
          )
        case ProfileFormat.GITHUB =>
          new UserMappingOAuth2Service[GitHubProfileResponse, DefaultOAuth2AuthorizationData](
            BaseOAuth2Service[GitHubProfileResponse](configuration),
            new GitHubProfileAuthentication(configuration)
          )
      },
      configuration
    )
  }

}

object DefaultOAuth2ServiceFactory {

  def service(configuration: OAuth2Configuration)(
      implicit backend: SttpBackend[Future, Any],
      ec: ExecutionContext
  ): OAuth2Service[AuthenticatedUser, OAuth2AuthorizationData] =
    DefaultOAuth2ServiceFactory().create(configuration)

  def apply(): DefaultOAuth2ServiceFactory = new DefaultOAuth2ServiceFactory
}
