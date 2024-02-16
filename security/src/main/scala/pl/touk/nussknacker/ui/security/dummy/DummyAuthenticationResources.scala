package pl.touk.nussknacker.ui.security.dummy

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsMissing
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1}
import pl.touk.nussknacker.security.AuthCredentials.PassedAuthCredentials
import pl.touk.nussknacker.ui.security.api.{
  AnonymousAccess,
  AuthenticatedUser,
  AuthenticationResources,
  FrontendStrategySettings
}
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthenticationConfiguration
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir._

import scala.collection.immutable.ListMap
import scala.concurrent.Future

class DummyAuthenticationResources(
    override val name: String,
    override val configuration: DummyAuthenticationConfiguration
) extends AuthenticationResources
    with AnonymousAccess {

  override type CONFIG = DummyAuthenticationConfiguration

  override protected val frontendStrategySettings: FrontendStrategySettings = FrontendStrategySettings.Browser

  override protected def authenticateReally(): AuthenticationDirective[AuthenticatedUser] = {
    reject(AuthenticationFailedRejection(CredentialsMissing, HttpChallenge("Dummy", "Dummy"))): Directive1[
      AuthenticatedUser
    ]
  }

  override protected def authenticateReally(credentials: PassedAuthCredentials): Future[Option[AuthenticatedUser]] = {
    Future.successful(None)
  }

  override protected def rawAuthCredentialsMethod: EndpointInput[Option[String]] = {
    auth.basic[Option[String]](new WWWAuthenticateChallenge("Dummy", ListMap.empty).realm("Dummy"))
  }

}
