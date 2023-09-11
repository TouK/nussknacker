package pl.touk.nussknacker.ui.security.oidc

import pl.touk.nussknacker.ui.security.api.{AuthCredentials, AuthenticatedUser}
import pl.touk.nussknacker.ui.security.oauth2._
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class OidcAuthenticationResources(name: String,
                                  realm: String,
                                  configuration: OidcAuthenticationConfiguration)
                                 (implicit ec: ExecutionContext,
                                  sttpBackend: SttpBackend[Future, Any])
  extends OAuth2AuthenticationResources(
    name = name,
    realm = realm,
    service = new CachingOAuth2Service(
      new UserMappingOAuth2Service(
        new OidcService(configuration),
        (userInfo: OpenIdConnectUserInfo) => OpenIdConnectProfile.getAuthenticatedUser(userInfo, configuration.oAuth2Configuration)
      ),
      configuration.oAuth2Configuration
    ),
    configuration = configuration.oAuth2Configuration
  ) {

  override def authenticationMethod(): sttp.tapir.EndpointInput.Auth[AuthCredentials, _] = ???

  override def authenticate(authCredentials: AuthCredentials): Future[Option[AuthenticatedUser]] = ???

}
