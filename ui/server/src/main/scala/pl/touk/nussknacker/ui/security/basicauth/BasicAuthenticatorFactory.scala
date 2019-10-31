package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.BasicHttpAuthenticator
import pl.touk.nussknacker.ui.security.api.{AuthenticatorFactory, DefaultAuthenticationConfiguration}
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.{AuthenticatorData, LoggedUserAuth}

class BasicAuthenticatorFactory extends AuthenticatorFactory with Directives {

  override def createAuthenticator(config: Config, classLoader: ClassLoader): AuthenticatorData = {
    val configuration = DefaultAuthenticationConfiguration.create(config)
    AuthenticatorData(createDirective(configuration), configuration)
  }

  def createDirective(config: DefaultAuthenticationConfiguration): LoggedUserAuth =
    SecurityDirectives.authenticateBasicAsync(
      authenticator = BasicHttpAuthenticator(config),
      realm = realm
    )
}

object BasicAuthenticatorFactory {
  def apply(): BasicAuthenticatorFactory = new BasicAuthenticatorFactory()
}