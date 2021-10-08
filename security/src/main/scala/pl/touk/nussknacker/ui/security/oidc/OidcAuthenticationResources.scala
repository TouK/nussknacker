package pl.touk.nussknacker.ui.security.oidc

import pl.touk.nussknacker.ui.security.oauth2._
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}


class OidcAuthenticationResources(name: String, realm: String, configuration: OidcAuthenticationConfiguration)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT])
  extends {
    val oAuth2Configuration = configuration.oAuth2Configuration
    val service = new CachingOAuth2Service(
          new UserMappingOAuth2Service(
            new OidcService(configuration),
            (userInfo: OpenIdConnectUserInfo) => OpenIdConnectProfile.getAuthenticatedUser(userInfo, oAuth2Configuration)
          )
      , oAuth2Configuration)
  } with OAuth2AuthenticationResources(name, realm, service, oAuth2Configuration)
