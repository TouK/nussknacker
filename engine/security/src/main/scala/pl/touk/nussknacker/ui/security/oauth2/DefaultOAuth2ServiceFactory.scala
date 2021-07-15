package pl.touk.nussknacker.ui.security.oauth2

import pl.touk.nussknacker.ui.security.api.{AuthenticatedUser, LoggedUser}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}


class DefaultOAuth2ServiceFactory extends OAuth2ServiceFactory {
  override def create(configuration: OAuth2Configuration)(implicit ec: ExecutionContext, backend: SttpBackend[Future, Nothing, NothingT]): OAuth2Service[AuthenticatedUser, OAuth2AuthorizationData] = {
    new CachingOAuth2Service(
      configuration.profileFormat.getOrElse {
        throw new Exception("profileFormat is missing in the authentication configuration")
      } match {
        case ProfileFormat.OIDC =>
          new UserMappingOAuth2Service(
            OpenIdConnectService(configuration),
            (userInfo: OpenIdConnectUserInfo) => OpenIdConnectProfile.getAuthenticatedUser(userInfo, configuration)
          )
        case ProfileFormat.GITHUB =>
          new UserMappingOAuth2Service(
            BaseOAuth2Service[GitHubProfileResponse](configuration),
            (profileResponse: GitHubProfileResponse) => GitHubProfile.getAuthenticatedUser(profileResponse, configuration)
          )
      }
      , configuration)
  }
}

object DefaultOAuth2ServiceFactory {
  def service(configuration: OAuth2Configuration)(implicit backend: SttpBackend[Future, Nothing, NothingT], ec: ExecutionContext): OAuth2Service[AuthenticatedUser, OAuth2AuthorizationData] =
    DefaultOAuth2ServiceFactory().create(configuration)

  def apply(): DefaultOAuth2ServiceFactory = new DefaultOAuth2ServiceFactory
}
