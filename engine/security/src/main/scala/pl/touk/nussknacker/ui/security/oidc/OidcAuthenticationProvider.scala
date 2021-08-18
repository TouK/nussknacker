package pl.touk.nussknacker.ui.security.oidc

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.AuthenticationProvider
import pl.touk.nussknacker.ui.security.oauth2.OAuth2AuthenticationResources
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}


class OidcAuthenticationProvider extends AuthenticationProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.authenticationConfigPath

  def name: String = "Oidc"

  override def createAuthenticationResources(config: Config, classLoader: ClassLoader)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): OAuth2AuthenticationResources = {
    new OidcAuthenticationResources(
      realm,
      config.as[OidcAuthenticationConfiguration](authenticationConfigPath).withDiscovery
    )
  }
}
