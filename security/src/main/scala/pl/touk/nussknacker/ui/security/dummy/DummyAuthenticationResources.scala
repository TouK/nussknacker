package pl.touk.nussknacker.ui.security.dummy

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsMissing
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1}
import pl.touk.nussknacker.ui.security.api.{AnonymousAccess, AuthCredentials, AuthenticatedUser, AuthenticationResources, FrontendStrategySettings}

import scala.concurrent.Future

class DummyAuthenticationResources(override val name: String, configuration: DummyAuthenticationConfiguration)
  extends AuthenticationResources
    with AnonymousAccess {

  val frontendStrategySettings: FrontendStrategySettings = FrontendStrategySettings.Browser

  val anonymousUserRole: Option[String] = Some(configuration.anonymousUserRole)

  def authenticateReally(): AuthenticationDirective[AuthenticatedUser] = {
    reject(AuthenticationFailedRejection(CredentialsMissing, HttpChallenge("Dummy", "Dummy"))): Directive1[AuthenticatedUser]
  }

  override def authenticationMethod(): sttp.tapir.EndpointInput.Auth[AuthCredentials, _] = ???

  override def authenticate(authCredentials: AuthCredentials): Future[Option[AuthenticatedUser]] = ???
}
