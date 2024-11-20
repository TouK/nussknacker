package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.NotificationApiEndpoints
import pl.touk.nussknacker.ui.notifications.NotificationService
import pl.touk.nussknacker.ui.security.api.AuthManager

import scala.concurrent.ExecutionContext

class NotificationApiHttpService(
    authManager: AuthManager,
    notificationService: NotificationService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with LazyLogging {

  private val notificationApiEndpoints = new NotificationApiEndpoints(
    authManager.authenticationEndpointInput()
  )

  expose {
    notificationApiEndpoints.notificationEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic { implicit loggedUser => processNameOpt =>
        notificationService
          .notifications(processNameOpt)
          .map { notificationList => success(notificationList) }
      }
  }

}
