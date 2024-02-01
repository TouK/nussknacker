package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.AuthenticationDirective
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.security.AuthCredentials
import sttp.client3.SttpBackend
import sttp.tapir.EndpointInput

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationResources extends Directives with FailFastCirceSupport {
  val name: String
  val frontendStrategySettings: FrontendStrategySettings

  // TODO: deprecated
  // The `authenticationMethod` & `authenticate(authCredentials: AuthCredentials)` are equivalent for the below one.
  // The `authenticationMethod` is to define what authentication method will be used in this resource. The latter one
  // will do the authentication based on the provided `AuthCredentials`. So, exactly what the `authenticate` directive
  // does. When we migrate fully to Tapir, we will get rid of Akka HTTP and the `authenticate` directive won't be needed.
  // Currently, in the implementation of `authenticate(authCredentials: AuthCredentials)` we use Akka HTTP classes,
  // so before we throw away Akka HTTP, we should migrate to some other implementations (e.g. from the Tapir's server
  // interpreter) or create our own.
  def authenticate(): Directive1[AuthenticatedUser]

  def authenticationMethod(): EndpointInput[AuthCredentials]

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
  this: AuthenticationResources =>

  implicit def executionContext: ExecutionContext
  val anonymousUserRole: Option[String]

  def authenticateReally(): AuthenticationDirective[AuthenticatedUser]

  def authenticateReally(authCredentials: AuthCredentials): Future[Option[AuthenticatedUser]]

  def authenticateOrPermitAnonymously(anonymousUser: AuthenticatedUser): AuthenticationDirective[AuthenticatedUser] = {
    def handleAuthorizationFailedRejection = handleRejections(
      RejectionHandler
        .newBuilder()
        // If the authorization rejection was caused by anonymous access,
        // we issue the Unauthorized status code with a challenge instead of the Forbidden
        .handle { case AuthorizationFailedRejection =>
          authenticateReally() { _ => reject }
        }
        .result()
    )

    authenticateReally().optional.flatMap(
      _.map(provide).getOrElse(
        handleAuthorizationFailedRejection.tmap(_ => anonymousUser)
      )
    )
  }

  def authenticateOrPermitAnonymously2(
      authCredentials: AuthCredentials,
      anonymousUser: AuthenticatedUser
  ): Future[Option[AuthenticatedUser]] = {
    authenticateReally(authCredentials)
//      .map {
//        case Some(loggedUser) =>
//          Some(loggedUser)
//        case None =>
//          Some(anonymousUser)
//      }
  }

  def authenticate(): Directive1[AuthenticatedUser] = {
    anonymousUserRole
      .map(role => AuthenticatedUser("anonymous", "anonymous", Set(role)))
      .map(authenticateOrPermitAnonymously)
      .getOrElse(authenticateReally())

  }

  override def authenticate(authCredentials: AuthCredentials): Future[Option[AuthenticatedUser]] = {
    anonymousUserRole
      .map(role => AuthenticatedUser("anonymous", "anonymous", Set(role)))
      .map(authenticateOrPermitAnonymously2(authCredentials, _))
      .getOrElse(authenticateReally(authCredentials))
  }

}

object AuthenticationResources {

  def apply(config: Config, classLoader: ClassLoader, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext
  ): AuthenticationResources = {
    implicit val sttpBackendImplicit: SttpBackend[Future, Any] = sttpBackend
    AuthenticationProvider(config, classLoader)
      .createAuthenticationResources(config, classLoader)
  }

}
