package pl.touk.nussknacker.ui.security

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.directives.AuthenticationDirective._
import akka.http.scaladsl.server.directives.{SecurityDirectives, _}
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.LoggedUserAuth
import pl.touk.nussknacker.ui.security.api.{AuthenticatorFactory, LoggedUser, Permission}

case class BasicAuthenticatorFactory() extends AuthenticatorFactory with Directives {
  override def createAuthenticator(config: Config): LoggedUserAuth = if (config.hasPath("usersFile")) {
    SecurityDirectives.authenticateBasicAsync("nussknacker",
      BasicHttpAuthenticator(config.getString("usersFile"))
    )
  } else provide(LoggedUser("Anonymous", Map("Default"->Permission.values)))

}
