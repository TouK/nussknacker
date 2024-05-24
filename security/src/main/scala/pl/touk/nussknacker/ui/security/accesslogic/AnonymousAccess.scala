package pl.touk.nussknacker.ui.security.accesslogic

import akka.http.scaladsl.server.Directives.{handleRejections, reject}
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive0, RejectionHandler}
import pl.touk.nussknacker.ui.security.api.{AuthenticatedUser, AuthenticationResources}

import scala.concurrent.Future

class AnonymousAccess(authenticationResources: AuthenticationResources) {

  val anonymousUserRole: Option[String] = authenticationResources.configuration.anonymousUserRole

  def handleAuthorizationFailedRejection: Directive0 = handleRejections(
    RejectionHandler
      .newBuilder()
      // If the authorization rejection was caused by anonymous access,
      // we issue the Unauthorized status code with a challenge instead of the Forbidden
      .handle { case AuthorizationFailedRejection => authenticationResources.authenticate() { _ => reject } }
      .result()
  )

  def authenticateWithAnonymousAccess(): Future[Option[AuthenticatedUser]] = anonymousUserRole
    .map { role =>
      Future.successful(Some(AuthenticatedUser.createAnonymousUser(Set(role))))
    }
    .getOrElse { Future.successful(None) }

}
