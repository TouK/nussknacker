package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.AuthenticationConfigurationFactory
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.LoggedUserAuth

class OAuth2AuthenticatorFactory extends AuthenticatorFactory with LazyLogging {

  override def createAuthenticator(config: Config, classLoader: ClassLoader): LoggedUserAuth = {
    val configuration = AuthenticationConfigurationFactory.oAuth2Config(config)
    val service = OAuth2ServiceFactory(configuration, classLoader)
    createAuthenticator(configuration, service)
  }

  def createAuthenticator(config: OAuth2Configuration, service: OAuth2Service): LoggedUserAuth = {
    SecurityDirectives.authenticateOAuth2Async(
      authenticator = OAuth2Authenticator(config, service),
      realm = realm
    )
  }
}

object OAuth2AuthenticatorFactory {
  def apply(): OAuth2AuthenticatorFactory = new OAuth2AuthenticatorFactory()
}
