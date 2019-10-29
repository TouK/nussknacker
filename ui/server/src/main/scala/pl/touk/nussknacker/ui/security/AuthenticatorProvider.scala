package pl.touk.nussknacker.ui.security

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.LoggedUserAuth
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthenticatorFactory
import pl.touk.nussknacker.ui.security.oauth2.OAuth2AuthenticatorFactory

object AuthenticatorProvider extends LazyLogging {
  def apply(config: Config, classLoader: ClassLoader): LoggedUserAuth = {
    val loaded = ScalaServiceLoader.loadClass[AuthenticatorFactory](classLoader) {
      AuthenticationConfigurationFactory.getBackendType(config) match {
        case AuthenticationBackend.OAuth2 => OAuth2AuthenticatorFactory()
        case _ => BasicAuthenticatorFactory()
      }
    }

    logger.info(s"Loaded authenticator backend: $loaded.")

    loaded.createAuthenticator(config, classLoader)
  }
}
