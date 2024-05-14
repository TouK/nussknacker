package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive0, Directive1, RejectionHandler}
import akka.http.scaladsl.server.Directives.{handleRejections, reject}
import pl.touk.nussknacker.security.AuthCredentials.{
  ImpersonatedAuthCredentials,
  NoCredentialsProvided,
  PassedAuthCredentials
}
import pl.touk.nussknacker.security.{AuthCredentials, ImpersonatedUserIdentity}
import pl.touk.nussknacker.ui.security.accesslogic.ImpersonatedAccess
import pl.touk.nussknacker.ui.security.api.AuthenticationManager.mappedAuthenticationEndpointInput
import sttp.tapir._

import scala.concurrent.{ExecutionContext, Future}

class AuthenticationManager(
    authenticationResources: AuthenticationResources,
    impersonationContext: ImpersonationContext
)(implicit ec: ExecutionContext) {

  lazy val authenticationRules: List[AuthenticationConfiguration.ConfigRule] =
    authenticationResources.configuration.rules
  lazy val isAdminImpersonationPossible: Boolean = authenticationResources.configuration.isAdminImpersonationPossible
  private val anonymousAccess                    = new AnonymousAccess(authenticationResources)
  private val impersonatedAccess                 = new ImpersonatedAccess(authenticationResources, impersonationContext)

  def authenticate(): Directive1[AuthenticatedUser] = {
    anonymousAccess.anonymousUserRole match {
      case Some(role) =>
        authenticationResources.authenticate().optional.flatMap {
          case Some(user) => impersonatedAccess.provideOrImpersonateUser(user)
          case None =>
            anonymousAccess.handleAuthorizationFailedRejection.tmap(_ =>
              AuthenticatedUser.createAnonymousUser(Set(role))
            )
        }
      case None => authenticationResources.authenticate().flatMap(impersonatedAccess.provideOrImpersonateUser)
    }
  }

  def authenticate(
      authCredentials: AuthCredentials
  ): Future[Option[AuthenticatedUser]] = {
    authCredentials match {
      case passedCredentials @ PassedAuthCredentials(_) => authenticationResources.authenticate(passedCredentials)
      case NoCredentialsProvided                        => anonymousAccess.authenticateWithAnonymousAccess()
      case ImpersonatedAuthCredentials(impersonatingUserAuthCredentials, impersonatedUserIdentity) =>
        impersonatedAccess.authenticateWithImpersonation(impersonatingUserAuthCredentials, impersonatedUserIdentity)
    }
  }

  def authenticationEndpointInput(): EndpointInput[AuthCredentials] = {
    authenticationResources
      .authenticationMethod()
      .and(ImpersonatedAccess.headerEndpointInput)
      .map(mappedAuthenticationEndpointInput)
  }

  private class AnonymousAccess(authenticationResources: AuthenticationResources) {

    val anonymousUserRole: Option[String] = authenticationResources.configuration.anonymousUserRole

    def handleAuthorizationFailedRejection: Directive0 = handleRejections(
      RejectionHandler
        .newBuilder()
        // If the authorization rejection was caused by anonymous access,
        // we issue the Unauthorized status code with a challenge instead of the Forbidden
        .handle { case AuthorizationFailedRejection => authenticationResources.authenticate() { _ => reject } }
        .result()
    )

    def authenticateWithAnonymousAccess(): Future[Option[AuthenticatedUser]] = Future {
      anonymousUserRole.map(role => AuthenticatedUser.createAnonymousUser(Set(role)))
    }

  }

}

object AuthenticationManager {

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
