package pl.touk.nussknacker.ui.security

import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.LoggedUserAuth

case class BasicAuthenticatorFactory() extends AuthenticatorFactory {
  override def createAuthenticator(config: Config): LoggedUserAuth =
    SecurityDirectives.authenticateBasicAsync("nussknacker",
      new BasicHttpAuthenticator(config.getString("usersFile"))
    )
}
