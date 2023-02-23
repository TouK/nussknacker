package pl.touk.nussknacker.ui.notifications

import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnDeployActionFailed, OnDeployActionSuccess}
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}
import pl.touk.nussknacker.ui.process.deployment.{AllInProgressDeploymentActionsResult, InProgressDeploymentActionsProvider, DeployInfo, DeploymentActionType}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}
import java.util.UUID
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class NotificationConfig(duration: FiniteDuration)

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

class NotificationService(currentDeployments: InProgressDeploymentActionsProvider,
                          store: NotificationsListener) {

  def notifications(user: LoggedUser, notificationsAfter: Option[Instant])(implicit ec: ExecutionContext, timeout: Timeout): Future[List[Notification]] = {
    Future.sequence(List(
      prepareDeploymentNotifications(user),
      Future.successful(userDeployments(user, notificationsAfter))
    )).map(_.flatten)
  }

  private def userDeployments(user: LoggedUser, notificationsAfter: Option[Instant]): Seq[Notification] = {
    store.dataFor(user, notificationsAfter).collect {
      case NotificationEvent(id, OnDeployActionFailed(_, reason), _, _, name) =>
        Notification(id, Some(name), s"Deployment of ${name.value} failed with ${reason.getMessage}", Some(NotificationType.error),
          List(DataToRefresh.state))
      case NotificationEvent(id, _: OnDeployActionSuccess, _, _, name) =>
        //We don't want to display this notification, not to confuse user, as
        //deployment may proceed asynchronously (e.g. in streaming-lite)
        Notification(id, Some(name), s"Deployment finished", None, List(DataToRefresh.versions, DataToRefresh.activity))
    }
  }

  private def prepareDeploymentNotifications(user: LoggedUser)(implicit ec: ExecutionContext, timeout: Timeout): Future[List[Notification]] = {
    currentDeployments.getAllInProgressDeploymentActions.map { case AllInProgressDeploymentActionsResult(deploymentInfos) =>
      deploymentInfos
        //no need to inform current user, DeployInfo takes username, not id
        .filterNot(_._2.userId == user.username)
        .map { case (k, v) => currentDeploymentToNotification(k, v) }.toList
    }
  }

  private def currentDeploymentToNotification(processName: ProcessName, deploymentInfo: DeployInfo): Notification = {
    val actionString = deploymentInfo.action match {
      case DeploymentActionType.Deployment => "deployed"
      case DeploymentActionType.Cancel => "cancelled"
    }
    //TODO: should it be displayed only once?
    Notification(UUID.randomUUID().toString, None, s"Scenario ${processName.value} is being $actionString by ${deploymentInfo.userId}", Some(NotificationType.success), Nil)
  }

}

private[notifications] case class NotificationEvent(id: String, event: ProcessChangeEvent, date: Instant, user: User, scenarioName: ProcessName)

