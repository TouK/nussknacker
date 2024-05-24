package pl.touk.nussknacker.ui.security.dummy

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsMissing
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1}
import pl.touk.nussknacker.security.AuthCredentials.PassedAuthCredentials
import pl.touk.nussknacker.ui.security.api.{AuthenticatedUser, AuthenticationResources, FrontendStrategySettings}
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir._

import scala.collection.immutable.ListMap
import scala.concurrent.Future

class DummyAuthenticationResources(
    override val name: String,
    override val configuration: DummyAuthenticationConfiguration
) extends AuthenticationResources {

  override type CONFIG = DummyAuthenticationConfiguration

  override protected val frontendStrategySettings: FrontendStrategySettings = FrontendStrategySettings.Browser

  override def authenticate(): AuthenticationDirective[AuthenticatedUser] =
    reject(AuthenticationFailedRejection(CredentialsMissing, HttpChallenge("Dummy", "Dummy"))): Directive1[
      AuthenticatedUser
    ]

  override def authenticate(authCredentials: PassedAuthCredentials): Future[Option[AuthenticatedUser]] =
    Future.successful(Option.empty)

  override def authenticationMethod(): EndpointInput[Credentials] = {
    auth.basic[Option[String]](new WWWAuthenticateChallenge("Dummy", ListMap.empty).realm("Dummy"))
  }

}
