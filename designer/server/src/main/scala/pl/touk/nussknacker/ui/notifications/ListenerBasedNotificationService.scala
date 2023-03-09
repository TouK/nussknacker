package pl.touk.nussknacker.ui.notifications

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnDeployActionFailed, OnDeployActionSuccess}
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.{Clock, Instant}
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class NotificationsListener(config: NotificationConfig,
                            fetchName: ProcessId => Future[Option[ProcessName]],
                            clock: Clock = Clock.systemUTC()) extends ProcessChangeListener with LazyLogging {

  //not too efficient, but we don't expect too much data...
  @volatile private var data: List[NotificationEvent] = Nil

  override def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: User): Unit = {
    val now = Instant.now(clock)
    fetchName(event.processId).onComplete {
      case Failure(e) => logger.error(s"Failed to retrieve scenario name for id: ${event.processId}", e)
      case Success(None) => logger.error(s"Failed to retrieve scenario name for id: ${event.processId}")
      case Success(Some(scenarioName)) => synchronized {
        data = NotificationEvent(UUID.randomUUID().toString, event, now, user, scenarioName) :: data
      }
    }
    filterOldNotifications(now)
  }

  private def filterOldNotifications(now: Instant): Unit = synchronized {
    data = data.filter(_.date.isAfter(now.minus(config.duration.toMillis, ChronoUnit.MILLIS)))
  }

  private[notifications] def dataFor(user: LoggedUser, notificationsAfter: Option[Instant]): List[NotificationEvent] = {
    filterOldNotifications(Instant.now(clock))
    data.filter(event => event.user.id == user.id && !notificationsAfter.exists(_.isAfter(event.date)))
  }

}

class ListenerBasedNotificationService(store: NotificationsListener) extends NotificationService {

  override def notifications(notificationsAfter: Option[Instant])(implicit user: LoggedUser, ec: ExecutionContext): Future[List[Notification]] = {
    Future.successful(userDeployments(user, notificationsAfter))
  }

  private def userDeployments(user: LoggedUser, notificationsAfter: Option[Instant]): List[Notification] = {
    store.dataFor(user, notificationsAfter).collect {
      case NotificationEvent(id, OnDeployActionFailed(_, reason), _, _, name) =>
        Notification.deploymentFailedNotification(id, name, reason.getMessage)
      case NotificationEvent(id, _: OnDeployActionSuccess, _, _, name) =>
        Notification.deploymentFinishedNotification(id, name)
    }
  }

}

private[notifications] case class NotificationEvent(id: String, event: ProcessChangeEvent, date: Instant, user: User, scenarioName: ProcessName)
