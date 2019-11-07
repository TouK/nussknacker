package pl.touk.nussknacker.ui.security

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.security.AuthenticatorFactory.AuthenticatorData
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthenticatorFactory
import pl.touk.nussknacker.ui.security.oauth2.OAuth2AuthenticatorFactory

object AuthenticatorProvider extends LazyLogging {
  def apply(config: Config, classLoader: ClassLoader, allCategories: List[String]): AuthenticatorData = {
    val loaded = ScalaServiceLoader.loadClass[AuthenticatorFactory](classLoader) {
      AuthenticationConfiguration.parseMethod(config) match {
        case AuthenticationMethod.OAuth2 => OAuth2AuthenticatorFactory()
        case _ => BasicAuthenticatorFactory()
      }
    }

    logger.info(s"Loaded authenticator method: $loaded.")

    loaded.createAuthenticator(config, classLoader, allCategories)
  }
}
