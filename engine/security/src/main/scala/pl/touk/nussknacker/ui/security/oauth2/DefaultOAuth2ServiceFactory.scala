package pl.touk.nussknacker.ui.security.oauth2

import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}


class DefaultOAuth2ServiceFactory extends OAuth2ServiceFactory {
  override def create(configuration: OAuth2Configuration, allCategories: List[String])(implicit ec: ExecutionContext, backend: SttpBackend[Future, Nothing, NothingT]): OAuth2Service[LoggedUser, OAuth2AuthorizationData] = {
    new CachingOAuth2Service(
      configuration.profileFormat.getOrElse {
        throw new Exception("profileFormat is missing in the authentication configuration")
      } match {
        case ProfileFormat.OIDC =>
          new UserMappingOAuth2Service(
            OpenIdConnectService(configuration),
            (userInfo: OpenIdConnectUserInfo) => OpenIdConnectProfile.getLoggedUser(userInfo, configuration, allCategories)
          )
        case ProfileFormat.GITHUB =>
          new UserMappingOAuth2Service(
            BaseOAuth2Service[GitHubProfileResponse](configuration),
            (profileResponse: GitHubProfileResponse) => GitHubProfile.getLoggedUser(profileResponse, configuration, allCategories)
          )
      }
      , configuration)
  }
}

object DefaultOAuth2ServiceFactory {
  def service(configuration: OAuth2Configuration, allCategories: List[String])(implicit backend: SttpBackend[Future, Nothing, NothingT], ec: ExecutionContext): OAuth2Service[LoggedUser, OAuth2AuthorizationData] =
    DefaultOAuth2ServiceFactory().create(configuration, allCategories)

  def apply(): DefaultOAuth2ServiceFactory = new DefaultOAuth2ServiceFactory
}
