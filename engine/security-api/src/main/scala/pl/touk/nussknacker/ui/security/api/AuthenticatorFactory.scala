package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.Config

trait AuthenticatorFactory {
  def createAuthenticator(config: Config): SecurityDirectives.Authenticator[LoggedUser]
}
