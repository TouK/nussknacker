package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.security.api._
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.EndpointInput.{ExtractFromRequest, Pair}
import sttp.tapir.EndpointOutput.OneOf
import sttp.tapir._
import sttp.tapir.internal.CombineParams

import scala.concurrent.{ExecutionContext, Future}

class BasicAuthenticationResources(realm: String, configuration: BasicAuthenticationConfiguration)(
    override implicit val executionContext: ExecutionContext
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

  override def authenticateReally(authCredentials: AuthCredentials): Future[Option[AuthenticatedUser]] = {
    authenticator.authenticate(authCredentials)
  }

}
