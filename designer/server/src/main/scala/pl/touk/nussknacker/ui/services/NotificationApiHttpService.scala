package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.NotificationApiEndpoints
import pl.touk.nussknacker.ui.notifications.NotificationService
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class NotificationApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    getProcessCategoryService: () => ProcessCategoryService,
    notificationService: NotificationService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, getProcessCategoryService, authenticator)
    with LazyLogging {

  private val notificationApiEndpoints = new NotificationApiEndpoints(authenticator.authenticationMethod())

  expose {
    val notificationsAfter = Some(Instant.now())

    notificationApiEndpoints.notificationEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic { user: LoggedUser => _ =>
        notificationService
          .notifications(notificationsAfter = notificationsAfter)(user, executionContext)
          .map { notificationList => success(notificationList) }
      }
  }

}
