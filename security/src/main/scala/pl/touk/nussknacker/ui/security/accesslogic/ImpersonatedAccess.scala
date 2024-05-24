package pl.touk.nussknacker.ui.security.accesslogic

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{failWith, optionalHeaderValueByName, provide}
import io.circe.parser.decode
import pl.touk.nussknacker.security.AuthCredentials.PassedAuthCredentials
import pl.touk.nussknacker.security.ImpersonatedUserData
import pl.touk.nussknacker.ui.security.accesslogic.ImpersonatedAccess.impersonateHeaderName
import pl.touk.nussknacker.ui.security.api.{AuthenticatedUser, AuthenticationResources}
import sttp.tapir.{EndpointIO, header}

import scala.concurrent.{ExecutionContext, Future}

class ImpersonatedAccess(authenticationResources: AuthenticationResources) {

  def provideOrImpersonateUser(user: AuthenticatedUser): Directive1[AuthenticatedUser] = {
    optionalHeaderValueByName(impersonateHeaderName).flatMap {
      case Some(impersonatedUserData) =>
        decode[ImpersonatedUserData](impersonatedUserData) match {
          case Right(userData) => provide(AuthenticatedUser.createImpersonatedUser(user, userData))
          case Left(error)     => failWith(error)
        }
      case None => provide(user)
    }
  }

  def authenticateWithImpersonation(
      impersonatingUserAuthCredentials: PassedAuthCredentials,
      impersonatedUserData: ImpersonatedUserData
  )(implicit ec: ExecutionContext): Future[Option[AuthenticatedUser]] = {
    for {
      impersonatingAuthUser <- authenticationResources.authenticate(impersonatingUserAuthCredentials)
      impersonatedAuthenticatedUser <- impersonatingAuthUser match {
        case Some(user) => Future.successful(Some(AuthenticatedUser.createImpersonatedUser(user, impersonatedUserData)))
        case None       => Future.successful(None)
      }
    } yield impersonatedAuthenticatedUser
  }

}

object ImpersonatedAccess {
  val impersonateHeaderName                                  = "Impersonate-User-Data"
  val headerEndpointInput: EndpointIO.Header[Option[String]] = header[Option[String]](impersonateHeaderName)
}
