package pl.touk.nussknacker.ui.security.oidc

import pl.touk.nussknacker.ui.security.oauth2._
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}


class OidcAuthenticationResources(name: String, realm: String, configuration: OidcAuthenticationConfiguration)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT])
  extends OAuth2AuthenticationResources(name, realm, new CachingOAuth2Service(
    new UserMappingOAuth2Service(
      new OidcService(configuration),
      (userInfo: OpenIdConnectUserInfo) => OpenIdConnectProfile.getAuthenticatedUser(userInfo, configuration.oAuth2Configuration)
    ), configuration.oAuth2Configuration), configuration.oAuth2Configuration)
