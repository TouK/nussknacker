package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import pl.touk.nussknacker.security.AuthCredentials.PassedAuthCredentials
import pl.touk.nussknacker.ui.security.api._
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir._

import scala.concurrent.{ExecutionContext, Future}

class BasicAuthenticationResources(
    override val name: String,
    realm: String,
    override val configuration: BasicAuthenticationConfiguration
)(
    implicit executionContext: ExecutionContext
) extends AuthenticationResources {

  override type CONFIG = BasicAuthenticationConfiguration

  private val authenticator = BasicHttpAuthenticator(configuration)

  override protected val frontendStrategySettings: FrontendStrategySettings = FrontendStrategySettings.Browser

  override def authenticate(): AuthenticationDirective[AuthenticatedUser] =
    SecurityDirectives.authenticateBasicAsync(
      authenticator = authenticator,
      realm = realm
    )

  override def authenticate(authCredentials: PassedAuthCredentials): Future[Option[AuthenticatedUser]] = {
    authenticator.authenticate(authCredentials)
  }

  override def authenticationMethod(): EndpointInput[Credentials] =
    auth.basic[Option[String]](WWWAuthenticateChallenge.basic.realm(realm))
}
