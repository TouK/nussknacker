package pl.touk.nussknacker.ui.security.accesslogic

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{optionalHeaderValueByName, provide, reject}
import cats.data.OptionT
import pl.touk.nussknacker.security.AuthCredentials.PassedAuthCredentials
import pl.touk.nussknacker.security.ImpersonatedUserIdentity
import pl.touk.nussknacker.ui.security.accesslogic.ImpersonatedAccess.impersonateHeaderName
import pl.touk.nussknacker.ui.security.api.{AuthenticatedUser, AuthenticationResources, ImpersonationContext}
import sttp.tapir.{EndpointIO, header}

import scala.concurrent.{ExecutionContext, Future}

class ImpersonatedAccess(
    authenticationResources: AuthenticationResources,
    impersonationContext: ImpersonationContext
)(implicit ec: ExecutionContext) {

  def provideOrImpersonateUser(user: AuthenticatedUser): Directive1[AuthenticatedUser] = {
    optionalHeaderValueByName(impersonateHeaderName).flatMap {
      case Some(impersonatedUserIdentity) =>
        impersonationContext.getImpersonatedUserData(impersonatedUserIdentity) match {
          case Some(impersonatedUserData) =>
            provide(AuthenticatedUser.createImpersonatedUser(user, impersonatedUserData))
          case None => reject
        }
      case None => provide(user)
    }
  }

  def authenticateWithImpersonation(
      impersonatingUserAuthCredentials: PassedAuthCredentials,
      impersonatedUserIdentity: ImpersonatedUserIdentity
  ): Future[Option[AuthenticatedUser]] = (for {
    impersonatingUser <- OptionT(authenticationResources.authenticate(impersonatingUserAuthCredentials))
    impersonatedUserData <- OptionT(
      Future { impersonationContext.getImpersonatedUserData(impersonatedUserIdentity.value) }
    )
  } yield AuthenticatedUser.createImpersonatedUser(impersonatingUser, impersonatedUserData)).value

}

object ImpersonatedAccess {
  val impersonateHeaderName                                  = "Impersonate-User-Identity"
  val headerEndpointInput: EndpointIO.Header[Option[String]] = header[Option[String]](impersonateHeaderName)
}
