package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.AuthenticationDirective
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationResources extends Directives with FailFastCirceSupport {
  val name: String
  val frontendStrategySettings: FrontendStrategySettings

  def authenticate(): Directive1[AuthenticatedUser]

  def authenticationMethod(): sttp.tapir.EndpointInput.Auth[AuthCredentials, _]

  def authenticate(authCredentials: AuthCredentials): Future[Option[AuthenticatedUser]]

  final lazy val routeWithPathPrefix: Route =
    pathPrefix("authentication" / name.toLowerCase()) {
      additionalRoute ~ frontendSettingsRoute
    }

  protected lazy val frontendSettingsRoute: Route =
    path("settings") {
      get {
        complete {
          ToResponseMarshallable(frontendStrategySettings)
        }
      }
    }
  protected lazy val additionalRoute: Route = Directives.reject
}

trait AnonymousAccess extends Directives {
  val anonymousUserRole: Option[String]

  def authenticateReally(): AuthenticationDirective[AuthenticatedUser]

  def authenticateOrPermitAnonymously(anonymousUser: AuthenticatedUser): AuthenticationDirective[AuthenticatedUser] = {
    def handleAuthorizationFailedRejection = handleRejections(RejectionHandler.newBuilder()
      // If the authorization rejection was caused by anonymous access,
      // we issue the Unauthorized status code with a challenge instead of the Forbidden
      .handle { case AuthorizationFailedRejection => authenticateReally() { _ => reject } }
      .result())

    authenticateReally().optional.flatMap(_.map(provide).getOrElse(
      handleAuthorizationFailedRejection.tmap(_ => anonymousUser)
    ))
  }

  def authenticate(): Directive1[AuthenticatedUser] = {
    anonymousUserRole.map(role => AuthenticatedUser("anonymous", "anonymous", Set(role)))
      .map(authenticateOrPermitAnonymously)
      .getOrElse(authenticateReally())

  }
}

object AuthenticationResources {
  def apply(config: Config,
            classLoader: ClassLoader,
            sttpBackend: SttpBackend[Future, Any])
           (implicit ec: ExecutionContext): AuthenticationResources = {
    implicit val sttpBackendImplicit: SttpBackend[Future, Any] = sttpBackend
    AuthenticationProvider(config, classLoader)
      .createAuthenticationResources(config, classLoader)
  }
}