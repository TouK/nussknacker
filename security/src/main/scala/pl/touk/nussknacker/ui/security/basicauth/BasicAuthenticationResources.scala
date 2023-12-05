package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import pl.touk.nussknacker.ui.security.api._
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir._

import scala.concurrent.{ExecutionContext, Future}

class BasicAuthenticationResources(realm: String, configuration: BasicAuthenticationConfiguration)(
    implicit executionContext: ExecutionContext
) extends AuthenticationResources
    with AnonymousAccess {

  val name: String = configuration.name

  val frontendStrategySettings: FrontendStrategySettings = FrontendStrategySettings.Browser

  val anonymousUserRole: Option[String] = configuration.anonymousUserRole

  private val authenticator = BasicHttpAuthenticator(configuration)

  def authenticateReally(): AuthenticationDirective[AuthenticatedUser] =
    SecurityDirectives.authenticateBasicAsync(
      authenticator = authenticator,
      realm = realm
    )

  override def authenticationMethod(): EndpointInput[AuthCredentials] = {
    auth.basic[AuthCredentials](WWWAuthenticateChallenge.basic.realm(realm))
  }

  override def authenticate(authCredentials: AuthCredentials): Future[Option[AuthenticatedUser]] = {
    authenticator.authenticate(authCredentials)
  }

}
