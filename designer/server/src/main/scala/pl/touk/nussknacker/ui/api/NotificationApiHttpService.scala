package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.NotificationApiEndpoints
import pl.touk.nussknacker.ui.notifications.NotificationService
import pl.touk.nussknacker.ui.security.api.AuthenticationManager

import scala.concurrent.ExecutionContext

class NotificationApiHttpService(
    authenticationManager: AuthenticationManager,
    notificationService: NotificationService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticationManager)
    with LazyLogging {

  private val notificationApiEndpoints = new NotificationApiEndpoints(
    authenticationManager.authenticationEndpointInput()
  )

  expose {
    notificationApiEndpoints.notificationEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic { implicit loggedUser => _ =>
        notificationService.notifications
          .map { notificationList => success(notificationList) }
      }
  }

}
