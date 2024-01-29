package pl.touk.nussknacker.ui.security.dummy

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsMissing
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1}
import pl.touk.nussknacker.ui.security.api.AuthenticatedUser
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.security.api.{AnonymousAccess, AuthenticationResources, FrontendStrategySettings}
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir._

import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}

class DummyAuthenticationResources(override val name: String, configuration: DummyAuthenticationConfiguration)(
    override implicit val executionContext: ExecutionContext
) extends AuthenticationResources
    with AnonymousAccess {

  val frontendStrategySettings: FrontendStrategySettings = FrontendStrategySettings.Browser

  val anonymousUserRole: Option[String] = Some(configuration.anonymousUserRole)

  def authenticateReally(): AuthenticationDirective[AuthenticatedUser] = {
    reject(AuthenticationFailedRejection(CredentialsMissing, HttpChallenge("Dummy", "Dummy"))): Directive1[
      AuthenticatedUser
    ]
  }

  override def authenticationMethod(): EndpointInput[AuthCredentials] =
    auth.basic(new WWWAuthenticateChallenge("Dummy", ListMap.empty).realm("Dummy"))

  override def authenticateReally(authCredentials: AuthCredentials): Future[Option[AuthenticatedUser]] =
    Future.successful(None)
}
