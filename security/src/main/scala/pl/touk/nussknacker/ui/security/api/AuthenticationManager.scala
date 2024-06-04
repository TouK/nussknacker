package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive0, Directive1, RejectionHandler}
import akka.http.scaladsl.server.Directives.{handleRejections, optionalHeaderValueByName, provide, reject}
import cats.data.OptionT
import pl.touk.nussknacker.security.AuthCredentials.{
  ImpersonatedAuthCredentials,
  NoCredentialsProvided,
  PassedAuthCredentials
}
import pl.touk.nussknacker.security.{AuthCredentials, ImpersonatedUserIdentity}
import pl.touk.nussknacker.ui.security.api.AuthenticationManager.{
  impersonateHeaderName,
  impersonationHeaderEndpointInput,
  mappedAuthenticationEndpointInput
}
import sttp.tapir._

import scala.concurrent.{ExecutionContext, Future}

class AuthenticationManager(authenticationResources: AuthenticationResources)(implicit ec: ExecutionContext) {

  lazy val authenticationRules: List[AuthenticationConfiguration.ConfigRule] =
    authenticationResources.configuration.rules
  lazy val isAdminImpersonationPossible: Boolean = authenticationResources.configuration.isAdminImpersonationPossible

  def authenticate(): Directive1[AuthenticatedUser] = authenticationResources.getAnonymousRole match {
    case Some(role) =>
      authenticationResources.authenticate().optional.flatMap {
        case Some(user) => provideOrImpersonateUser(user)
        case None => handleAuthorizationFailedRejection.tmap(_ => AuthenticatedUser.createAnonymousUser(Set(role)))
      }
    case None => authenticationResources.authenticate().flatMap(provideOrImpersonateUser)
  }

  def authenticate(authCredentials: AuthCredentials): Future[Option[AuthenticatedUser]] = authCredentials match {
    case passedCredentials @ PassedAuthCredentials(_) => authenticationResources.authenticate(passedCredentials)
    case NoCredentialsProvided                        => authenticateWithAnonymousAccess()
    case ImpersonatedAuthCredentials(impersonatingUserAuthCredentials, impersonatedUserIdentity) =>
      authenticateWithImpersonation(impersonatingUserAuthCredentials, impersonatedUserIdentity)
  }

  def authenticationEndpointInput(): EndpointInput[AuthCredentials] =
    authenticationResources
      .authenticationMethod()
      .and(impersonationHeaderEndpointInput)
      .map(mappedAuthenticationEndpointInput)

  private def provideOrImpersonateUser(user: AuthenticatedUser): Directive1[AuthenticatedUser] = {
    optionalHeaderValueByName(impersonateHeaderName).flatMap {
      case Some(impersonatedUserIdentity) =>
        authenticationResources.getImpersonatedUserData(impersonatedUserIdentity) match {
          case Some(impersonatedUserData) =>
            provide(AuthenticatedUser.createImpersonatedUser(user, impersonatedUserData))
          case None => reject
        }
      case None => provide(user)
    }
  }

  private def authenticateWithImpersonation(
      impersonatingUserAuthCredentials: PassedAuthCredentials,
      impersonatedUserIdentity: ImpersonatedUserIdentity,
  ): Future[Option[AuthenticatedUser]] = (for {
    impersonatingUser <- OptionT(authenticationResources.authenticate(impersonatingUserAuthCredentials))
    impersonatedUserData <- OptionT(
      Future { authenticationResources.getImpersonatedUserData(impersonatedUserIdentity.value) }
    )
  } yield AuthenticatedUser.createImpersonatedUser(impersonatingUser, impersonatedUserData)).value

  private def handleAuthorizationFailedRejection: Directive0 = handleRejections(
    RejectionHandler
      .newBuilder()
      // If the authorization rejection was caused by anonymous access,
      // we issue the Unauthorized status code with a challenge instead of the Forbidden
      .handle { case AuthorizationFailedRejection => authenticationResources.authenticate() { _ => reject } }
      .result()
  )

  private def authenticateWithAnonymousAccess(): Future[Option[AuthenticatedUser]] = Future {
    authenticationResources.getAnonymousRole.map(role => AuthenticatedUser.createAnonymousUser(Set(role)))
  }

}

object AuthenticationManager {
  val impersonateHeaderName = "Impersonate-User-Identity"
  val impersonationHeaderEndpointInput: EndpointIO.Header[Option[String]] =
    header[Option[String]](impersonateHeaderName)

  val mappedAuthenticationEndpointInput: Mapping[(Option[String], Option[String]), AuthCredentials] =
    Mapping.fromDecode[(Option[String], Option[String]), AuthCredentials] {
      case (Some(passedCredentials), None) => DecodeResult.Value(PassedAuthCredentials(passedCredentials))
      case (Some(passedCredentials), Some(identity)) =>
        DecodeResult.Value(
          ImpersonatedAuthCredentials(PassedAuthCredentials(passedCredentials), ImpersonatedUserIdentity(identity))
        )
      case (None, None) => DecodeResult.Value(NoCredentialsProvided)
      case _            => DecodeResult.Missing
    } {
      case PassedAuthCredentials(value) => (Some(value), None)
      case NoCredentialsProvided        => (None, None)
      case ImpersonatedAuthCredentials(impersonating, impersonatedUserIdentity) =>
        (Some(impersonating.value), Some(impersonatedUserIdentity.value))
    }

}
