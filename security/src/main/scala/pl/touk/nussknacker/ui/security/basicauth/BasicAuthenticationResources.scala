package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.security.AuthCredentials.PassedAuthCredentials
import pl.touk.nussknacker.ui.security.api._
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir._

import scala.concurrent.{ExecutionContext, Future}

class BasicAuthenticationResources(realm: String, configuration: BasicAuthenticationConfiguration)(
    override implicit val executionContext: ExecutionContext
) extends AuthenticationResources
    with AnonymousAccess {

  private val authenticator = BasicHttpAuthenticator(configuration)

  override protected val name: String = configuration.name

  override protected val frontendStrategySettings: FrontendStrategySettings = FrontendStrategySettings.Browser

  override protected val anonymousUserRole: Option[String] = configuration.anonymousUserRole

  override protected def authenticateReally(): AuthenticationDirective[AuthenticatedUser] =
    SecurityDirectives.authenticateBasicAsync(
      authenticator = authenticator,
      realm = realm
    )

  override def authenticationMethod(): EndpointInput[AuthCredentials] = {
    // todo: take into consideratoin anonymousUserRole.toSet
    auth
      .basic[String](WWWAuthenticateChallenge.basic.realm(realm))
      .map(Mapping.from[String, AuthCredentials](AuthCredentials.fromString)(_.stringify))
  }

  override protected def authenticateReally(credentials: PassedAuthCredentials): Future[Option[AuthenticatedUser]] = {
    authenticator.authenticate(credentials)
  }
}
