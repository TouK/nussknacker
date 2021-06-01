package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticatorFactory {
  import AuthenticatorFactory._

  val realm = "nussknacker"

  //TODO: Extract putting allCategories in up level. Authenticator should return only Authenticated User(id, roles)
  // mapping Authenticated User with all Categories should be do only at one place
  def createAuthenticator(config: Config, classLoader: ClassLoader, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticatorData
}

object AuthenticatorFactory {
  type LoggedUserAuth = AuthenticationDirective[LoggedUser]

  case class AuthenticatorData(directive: LoggedUserAuth, config: AuthenticationConfiguration, routes: List[Route] = List.empty)
}



