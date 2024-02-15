package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.AuthenticationDirective
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.security.AuthCredentials.PassedAuthCredentials
import pl.touk.nussknacker.ui.security.api.AnonymousAccess.optionalStringToAuthCredentialsMapping
import sttp.client3.SttpBackend
import sttp.tapir.{DecodeResult, EndpointInput, Mapping}

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationResources extends Directives with FailFastCirceSupport {

  type CONFIG <: AuthenticationConfiguration
  def name: String
  def configuration: CONFIG

  protected def frontendStrategySettings: FrontendStrategySettings

  // TODO: deprecated
  // The `authenticationMethod` & `authenticate(authCredentials: AuthCredentials)` are equivalent for the below one.
  // The `authenticationMethod` is to define what authentication method will be used in this resource. The latter one
  // will do the authentication based on the provided `AuthCredentials`. So, exactly what the `authenticate` directive
  // does. When we migrate fully to Tapir, we will get rid of Akka HTTP and the `authenticate` directive won't be needed.
  // Currently, in the implementation of `authenticate(authCredentials: AuthCredentials)` we use Akka HTTP classes,
  // so before we throw away Akka HTTP, we should migrate to some other implementations (e.g. from the Tapir's server
  // interpreter) or create our own.
  def authenticate(): Directive1[AuthenticatedUser]

  def authenticate(authCredentials: AuthCredentials): Future[Option[AuthenticatedUser]]

  def authenticationMethod(): EndpointInput[AuthCredentials]

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

object AuthenticationResources {

  def apply(config: Config, classLoader: ClassLoader, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext
  ): AuthenticationResources = {
    implicit val sttpBackendImplicit: SttpBackend[Future, Any] = sttpBackend
    AuthenticationProvider(config, classLoader)
      .createAuthenticationResources(config, classLoader)
  }

}

trait AnonymousAccess extends Directives {
  this: AuthenticationResources =>

  protected def authenticateReally(): AuthenticationDirective[AuthenticatedUser]

  protected def rawAuthCredentialsMethod: EndpointInput[Option[String]]

  protected def authenticateReally(credentials: PassedAuthCredentials): Future[Option[AuthenticatedUser]]

  override final def authenticationMethod(): EndpointInput[AuthCredentials] = {
    rawAuthCredentialsMethod.map(optionalStringToAuthCredentialsMapping(configuration.anonymousUserRole.isDefined))
  }

  override final def authenticate(authCredentials: AuthCredentials): Future[Option[AuthenticatedUser]] = {
    authCredentials match {
      case credentials @ AuthCredentials.PassedAuthCredentials(_) =>
        authenticateReally(credentials)
      case AuthCredentials.AnonymousAccess =>
        Future.successful(Some(AuthenticatedUser.createAnonymousUser(configuration.anonymousUserRole.toSet)))
    }
  }

  override final def authenticate(): Directive1[AuthenticatedUser] = {
    configuration.anonymousUserRole match {
      case Some(role) =>
        authenticateOrPermitAnonymously(AuthenticatedUser.createAnonymousUser(Set(role)))
      case None =>
        authenticateReally()
    }
  }

  private def authenticateOrPermitAnonymously(
      anonymousUser: AuthenticatedUser
  ): AuthenticationDirective[AuthenticatedUser] = {
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

}

object AnonymousAccess {

  def optionalStringToAuthCredentialsMapping(
      anonymousAccessEnabled: Boolean
  ): Mapping[Option[String], AuthCredentials] =
    Mapping
      .fromDecode[Option[String], AuthCredentials] {
        case Some(value) => DecodeResult.Value(AuthCredentials.PassedAuthCredentials(value))
        case None if anonymousAccessEnabled =>
          DecodeResult.Value(AuthCredentials.AnonymousAccess)
        case None =>
          DecodeResult.Missing
      } {
        case AuthCredentials.PassedAuthCredentials(credentials) => Some(credentials)
        case AuthCredentials.AnonymousAccess                    => None
      }

}
