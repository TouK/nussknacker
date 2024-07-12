package pl.touk.nussknacker.ui.security.oidc

import pl.touk.nussknacker.ui.security.oauth2._
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class OidcAuthenticationResources(name: String, configuration: OidcAuthenticationConfiguration)(
    implicit ec: ExecutionContext,
    sttpBackend: SttpBackend[Future, Any]
) extends OAuth2AuthenticationResources(
      name = name,
      service = new CachingOAuth2Service(
        new UserMappingOAuth2Service[OidcUserInfo, DefaultOidcAuthorizationData](
          new OidcService(configuration),
          new OidcProfileAuthentication(configuration.oAuth2Configuration)
        ),
        configuration.oAuth2Configuration
      ),
      configuration = configuration.oAuth2Configuration
    )
