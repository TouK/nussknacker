package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.LoggedUserAuth
import pl.touk.nussknacker.ui.security.{AuthenticationConfigurationFactory, BasicHttpAuthenticator}

class BasicAuthenticatorFactory extends AuthenticatorFactory with Directives {
  override def createAuthenticator(config: Config, classLoader: ClassLoader): LoggedUserAuth
    = createAuthenticator(AuthenticationConfigurationFactory.basicAuthConfig(config))

  def createAuthenticator(config: BasicAuthConfiguration): LoggedUserAuth =
    SecurityDirectives.authenticateBasicAsync(
      authenticator = BasicHttpAuthenticator(config),
      realm = realm
    )
}

object BasicAuthenticatorFactory {
  def apply(): BasicAuthenticatorFactory = new BasicAuthenticatorFactory()
}