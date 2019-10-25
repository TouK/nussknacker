package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.server.directives.AuthenticationDirective
import com.typesafe.config.Config

trait AuthenticatorFactory {
  val realm = "nussknacker"
  import AuthenticatorFactory._

  def createAuthenticator(config: Config, classLoader: ClassLoader): LoggedUserAuth
}

object AuthenticatorFactory {
  type LoggedUserAuth = AuthenticationDirective[LoggedUser]
}



