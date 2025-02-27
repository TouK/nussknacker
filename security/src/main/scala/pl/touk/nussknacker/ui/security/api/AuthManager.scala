package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{
  AuthorizationFailedRejection,
  Directive0,
  Directive1,
  Directives,
  RejectionHandler,
  Route
}
import akka.http.scaladsl.server.Directives.{complete, handleRejections, optionalHeaderValueByName, provide, reject}
import pl.touk.nussknacker.security.{AuthCredentials, ImpersonatedUserIdentity}
import pl.touk.nussknacker.security.AuthCredentials.{
  ImpersonatedAuthCredentials,
  NoCredentialsProvided,
  PassedAuthCredentials
}
import pl.touk.nussknacker.ui.security.api.AuthManager.{impersonateHeaderName, ImpersonationConsideringInputEndpoint}
import pl.touk.nussknacker.ui.security.api.CreationError.ImpersonationNotAllowed
import pl.touk.nussknacker.ui.security.api.SecurityError._
import sttp.tapir._

import scala.concurrent.{ExecutionContext, Future}

class AuthManager(protected val authenticationResources: AuthenticationResources)(implicit ec: ExecutionContext)
    extends AkkaBasedAuthManager {

  protected lazy val isAdminImpersonationPossible: Boolean =
    authenticationResources.configuration.isAdminImpersonationPossible
  protected lazy val authenticationRules: List[AuthenticationConfiguration.ConfigRule] =
    authenticationResources.configuration.rules

  def authenticate(authCredentials: AuthCredentials): Future[Either[AuthenticationError, AuthenticatedUser]] =
    authCredentials match {
      case NoCredentialsProvided => authenticateWithAnonymousAccess()
      case passedCredentials @ PassedAuthCredentials(_) =>
        authenticationResources.authenticate(passedCredentials).map(_.toRight(CannotAuthenticateUser))
      case ImpersonatedAuthCredentials(impersonatingUserAuthCredentials, impersonatedUserIdentity) =>
        authenticateWithImpersonation(impersonatingUserAuthCredentials, impersonatedUserIdentity)
    }

  def authenticationEndpointInput(): EndpointInput[AuthCredentials] =
    authenticationResources
      .authenticationMethod()
      .withPossibleImpersonation(authenticationResources.getAnonymousRole.isDefined)

  def authorize(user: AuthenticatedUser): Either[AuthorizationError, LoggedUser] = {
    if (user.roles.nonEmpty)
      // TODO: This is strange that we call authenticator.authenticate and the first thing that we do with the returned user is
      //       creation of another user representation based on authenticator.configuration. Shouldn't we just return the LoggedUser?
      LoggedUser.create(user, authenticationRules, isAdminImpersonationPossible).left.map {
        case ImpersonationNotAllowed => ImpersonationMissingPermissionError
      }
    else Left(InsufficientPermission)
  }

  private def authenticateWithImpersonation(
      impersonatingUserAuthCredentials: PassedAuthCredentials,
      impersonatedUserIdentity: ImpersonatedUserIdentity,
  ): Future[Either[AuthenticationError, AuthenticatedUser]] = {
    authenticationResources.authenticate(impersonatingUserAuthCredentials).map {
      case Some(impersonatingUser) => handleImpersonation(impersonatingUser, impersonatedUserIdentity.value)
      case None                    => Left(CannotAuthenticateUser)
    }
  }

  private def authenticateWithAnonymousAccess(): Future[Either[AuthenticationError, AuthenticatedUser]] = Future {
    authenticationResources.getAnonymousRole
      .map(role => AuthenticatedUser.createAnonymousUser(Set(role)))
      .toRight(CannotAuthenticateUser)
  }

  protected def handleImpersonation(
      impersonatingUser: AuthenticatedUser,
      impersonatedUserIdentity: String
  ): Either[ImpersonationAuthenticationError, AuthenticatedUser] =
    authenticationResources.impersonationSupport.getImpersonatedUserDataWithSupportCheck(
      impersonatedUserIdentity
    ) match {
      case Right(maybeImpersonatedUserData) =>
        maybeImpersonatedUserData match {
          case Some(impersonatedUserData) =>
            Right(AuthenticatedUser.createImpersonatedUser(impersonatingUser, impersonatedUserData))
          case None => Left(ImpersonatedUserNotExistsError)
        }
      case Left(ImpersonationNotSupported) => Left(ImpersonationNotSupportedError)
    }

}

object AuthManager {
  val impersonateHeaderName = "Nu-Impersonate-User-Identity"

  implicit class ImpersonationConsideringInputEndpoint(underlying: EndpointInput[Option[PassedAuthCredentials]]) {

    def withPossibleImpersonation(anonymousAccessEnabled: Boolean): EndpointInput[AuthCredentials] = {
      underlying
        .and(impersonationHeaderEndpointInput)
        .map { mappedAuthenticationEndpointInput(anonymousAccessEnabled) }
    }

    private def impersonationHeaderEndpointInput: EndpointIO.Header[Option[String]] =
      header[Option[String]](impersonateHeaderName)

    private def mappedAuthenticationEndpointInput(
        anonymousAccessEnabled: Boolean
    ): Mapping[(Option[PassedAuthCredentials], Option[String]), AuthCredentials] =
      Mapping.fromDecode[(Option[PassedAuthCredentials], Option[String]), AuthCredentials] {
        case (Some(passedCredentials), None) => DecodeResult.Value(passedCredentials)
        case (Some(passedCredentials), Some(identity)) =>
          DecodeResult.Value(
            ImpersonatedAuthCredentials(passedCredentials, ImpersonatedUserIdentity(identity))
          )
        case (None, None) if !anonymousAccessEnabled => DecodeResult.Missing
        case (None, None)                            => DecodeResult.Value(NoCredentialsProvided)
        // In case of a situation where we receive impersonation header without credentials of an impersonating user
        // we return DecodeResult.Missing instead of NoCredentialsProvided as we require impersonating user to be authenticated
        case (None, Some(_)) => DecodeResult.Missing
      } {
        case PassedAuthCredentials(value) => (Some(PassedAuthCredentials(value)), None)
        case NoCredentialsProvided        => (None, None)
        case ImpersonatedAuthCredentials(impersonating, impersonatedUserIdentity) =>
          (Some(impersonating), Some(impersonatedUserIdentity.value))
      }

  }

}

// Akka based auth logic is separated from the rest of AuthManager since it will be easier to remove it once we fully
// migrate to Tapir. Also it helps with readability within AuthManager as Akka and Tapir logic look different.
private[api] trait AkkaBasedAuthManager {
  self: AuthManager =>

  def authenticate(): Directive1[AuthenticatedUser] = authenticationResources.getAnonymousRole match {
    case Some(role) =>
      authenticationResources.authenticate().optional.flatMap {
        case Some(user) => provideOrImpersonateUserDirective(user)
        case None => handleAuthorizationFailedRejection.tmap(_ => AuthenticatedUser.createAnonymousUser(Set(role)))
      }
    case None => authenticationResources.authenticate().flatMap(provideOrImpersonateUserDirective)
  }

  def authorizeRoute(user: AuthenticatedUser)(secureRoute: LoggedUser => Route): Route =
    Directives.authorize(user.roles.nonEmpty) {
      LoggedUser.create(user, authenticationRules, isAdminImpersonationPossible) match {
        case Right(loggedUser) => secureRoute(loggedUser)
        case Left(ImpersonationNotAllowed) =>
          complete(StatusCodes.Forbidden -> ImpersonationMissingPermissionError.errorMessage)
      }
    }

  private def provideOrImpersonateUserDirective(user: AuthenticatedUser): Directive1[AuthenticatedUser] = {
    optionalHeaderValueByName(impersonateHeaderName).flatMap {
      case Some(impersonatedUserIdentity) =>
        handleImpersonation(user, impersonatedUserIdentity) match {
          case Right(impersonatedUser) => provide(impersonatedUser)
          case Left(ImpersonatedUserNotExistsError) =>
            complete(StatusCodes.Forbidden, ImpersonatedUserNotExistsError.errorMessage)
          case Left(ImpersonationNotSupportedError) =>
            complete(StatusCodes.NotImplemented, ImpersonationNotSupportedError.errorMessage)
        }
      case None => provide(user)
    }
  }

  private def handleAuthorizationFailedRejection: Directive0 = handleRejections(
    RejectionHandler
      .newBuilder()
      // If the authorization rejection was caused by anonymous access,
      // we issue the Unauthorized status code with a challenge instead of the Forbidden
      .handle { case AuthorizationFailedRejection => authenticationResources.authenticate() { _ => reject } }
      .result()
  )

}
