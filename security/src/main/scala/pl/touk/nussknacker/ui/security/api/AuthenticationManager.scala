package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.server.Directive1
import io.circe.parser._
import io.circe.syntax._
import pl.touk.nussknacker.security.AuthCredentials.{
  ImpersonatedAuthCredentials,
  NoCredentialsProvided,
  PassedAuthCredentials
}
import pl.touk.nussknacker.security.{AuthCredentials, ImpersonatedUserData}
import pl.touk.nussknacker.ui.security.accesslogic.{AnonymousAccess, ImpersonatedAccess}
import sttp.tapir._

import scala.concurrent.{ExecutionContext, Future}

class AuthenticationManager(authenticationResources: AuthenticationResources) {

  lazy val authenticationRules: List[AuthenticationConfiguration.ConfigRule] =
    authenticationResources.configuration.rules
  private val anonymousAccess    = new AnonymousAccess(authenticationResources)
  private val impersonatedAccess = new ImpersonatedAccess(authenticationResources)

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
  )(implicit ec: ExecutionContext): Future[Option[AuthenticatedUser]] = {
    authCredentials match {
      case passedCredentials @ PassedAuthCredentials(_) => authenticationResources.authenticate(passedCredentials)
      case NoCredentialsProvided                        => anonymousAccess.authenticateWithAnonymousAccess()
      case ImpersonatedAuthCredentials(impersonatingUserAuthCredentials, impersonatedUserData) =>
        impersonatedAccess.authenticateWithImpersonation(impersonatingUserAuthCredentials, impersonatedUserData)
    }
  }

  def authenticationEndpointInput(): EndpointInput[AuthCredentials] = {
    authenticationResources
      .authenticationMethod()
      .and(ImpersonatedAccess.headerEndpointInput)
      .map(
        Mapping.fromDecode[(Option[String], Option[String]), AuthCredentials] {
          case (Some(passedCredentials), None) => DecodeResult.Value(PassedAuthCredentials(passedCredentials))
          case (Some(passedCredentials), Some(impersonatedUserData)) =>
            decode[ImpersonatedUserData](impersonatedUserData) match {
              case Right(impersonatedUserData) =>
                DecodeResult.Value(
                  ImpersonatedAuthCredentials(PassedAuthCredentials(passedCredentials), impersonatedUserData)
                )
              case Left(error) => DecodeResult.Error(impersonatedUserData, error)
            }
          case (None, None) => DecodeResult.Value(NoCredentialsProvided)
          case _            => DecodeResult.Missing
        } {
          case PassedAuthCredentials(value) => (Some(value), None)
          case NoCredentialsProvided        => (None, None)
          case ImpersonatedAuthCredentials(impersonating, impersonated) =>
            (Some(impersonating.value), Some(impersonated.asJson.noSpaces))
        }
      )
  }

}
