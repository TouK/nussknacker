package pl.touk.nussknacker.ui.security.oidc

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.{AuthenticationProvider, ImpersonationContext}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2AuthenticationResources
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class OidcAuthenticationProvider extends AuthenticationProvider {

  def name: String = "Oidc"

  override def createAuthenticationResources(
      config: Config,
      classLoader: ClassLoader
  )(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Any]): OAuth2AuthenticationResources = {
    new OidcAuthenticationResources(
      name,
      realm,
      OidcAuthenticationConfiguration.createWithDiscovery(config)
    )
  }

  // TODO implement impersonation support for Oidc
  override def createImpersonationContext(config: Config): ImpersonationContext = (_: String) => None
}

object OidcAuthenticationProvider extends OidcAuthenticationProvider
