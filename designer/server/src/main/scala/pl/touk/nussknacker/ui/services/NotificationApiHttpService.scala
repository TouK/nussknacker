package pl.touk.nussknacker.ui.services

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.NotificationApiEndpoints
import pl.touk.nussknacker.ui.notifications.NotificationService
import pl.touk.nussknacker.ui.security.api.AuthenticationResources

import scala.concurrent.ExecutionContext

class NotificationApiHttpService(
    authenticator: AuthenticationResources,
    notificationService: NotificationService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator)
    with LazyLogging {

  private val notificationApiEndpoints = new NotificationApiEndpoints(authenticator.authenticationMethod())

  expose {
    notificationApiEndpoints.notificationEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic { implicit loggedUser => _ =>
        notificationService.notifications
          .map { notificationList => success(notificationList) }
      }
  }

}
