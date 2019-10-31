package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config

trait AuthenticatorFactory {
  import AuthenticatorFactory._

  val realm = "nussknacker"

  def createAuthenticator(config: Config, classLoader: ClassLoader): AuthenticatorData
}


object AuthenticatorFactory {
  type LoggedUserAuth = AuthenticationDirective[LoggedUser]

  case class AuthenticatorData(directive: LoggedUserAuth, config: AuthenticationConfiguration, routes: List[Route] = List.empty)
}



