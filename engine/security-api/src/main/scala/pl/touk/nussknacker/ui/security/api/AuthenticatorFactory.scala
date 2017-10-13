package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.server.directives.AuthenticationDirective
import com.typesafe.config.Config

trait AuthenticatorFactory {

  import AuthenticatorFactory._

  def createAuthenticator(config: Config): LoggedUserAuth
}

object AuthenticatorFactory {
  type LoggedUserAuth = AuthenticationDirective[LoggedUser]

}



