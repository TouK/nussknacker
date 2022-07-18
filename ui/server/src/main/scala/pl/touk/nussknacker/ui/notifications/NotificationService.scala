package pl.touk.nussknacker.ui.notifications

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnDeployActionFailed, OnDeployActionSuccess}
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}
import pl.touk.nussknacker.ui.notifications.NotificationAction._
import pl.touk.nussknacker.ui.process.deployment.{DeployInfo, DeploymentActionType, DeploymentStatus, DeploymentStatusResponse}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class NotificationsListener extends ProcessChangeListener {

  private val timeout = 10 minutes

  private val data: ArrayBuffer[NotificationEvent] = ArrayBuffer()

  override def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: User): Unit = synchronized {
    val now = Instant.now()
    data.append(NotificationEvent(UUID.randomUUID().toString, event, now, user))
    filterOldNotifications(now)
  }

  private def filterOldNotifications(now: Instant): Unit = {
    data.zipWithIndex.find(_._1.date.isBefore(now.minus(timeout.toMillis, ChronoUnit.MILLIS))).foreach(i => data.remove(i._2))
  }

  private[notifications] def dataFor(user: LoggedUser, notificationsAfter: Option[Instant]): List[NotificationEvent] = synchronized {
    filterOldNotifications(Instant.now())
    data.filter(event => event.user.id == user.id && !notificationsAfter.exists(_.isBefore(event.date))).toList
  }


}

class NotificationService(managementActor: ActorRef,
                          store: NotificationsListener) {

  def notifications(user: LoggedUser, notificationsAfter: Option[Instant])(implicit ec: ExecutionContext, timeout: Timeout): Future[List[Notification]] = {
    Future.sequence(List(
      prepareDeploymentNotifications(user),
      Future.successful(userDeployments(user, notificationsAfter))
    )).map(_.flatten)
  }

  private def userDeployments(user: LoggedUser, notificationsAfter: Option[Instant]): Seq[Notification] = {
    store.dataFor(user, notificationsAfter).collect {
      case NotificationEvent(id, OnDeployActionFailed(scenarioId, reason), _, _) =>
        Notification(id, s"Deployment failed with ${reason.getMessage}", NotificationType.warning, Some(deploymentFailed))
      case NotificationEvent(id, e: OnDeployActionSuccess, _, _) =>
        Notification(id, "Deployment finished", NotificationType.info, Some(deploymentFinished))
    }
  }

  private def prepareDeploymentNotifications(user: LoggedUser)(implicit ec: ExecutionContext, timeout: Timeout): Future[List[Notification]] = {
    (managementActor ? DeploymentStatus)
      .mapTo[DeploymentStatusResponse]
      .map {
        case DeploymentStatusResponse(deploymentInfos) =>
          deploymentInfos
            //no need to inform current user
            .filterNot(_._2.userId == user.id)
            .map { case (k, v) => currentDeploymentToNotification(k, v) }.toList
      }
  }

  private def currentDeploymentToNotification(processName: ProcessName, deploymentInfo: DeployInfo): Notification = {
    val actionString = deploymentInfo.action match {
      case DeploymentActionType.Deployment => "deployed"
      case DeploymentActionType.Cancel => "cancelled"
    }
    //TODO: should it be displayed only once?
    Notification(UUID.randomUUID().toString, s"Scenario ${processName.value} is being $actionString by ${deploymentInfo.userId}", NotificationType.info, None)
  }

}

private[notifications] case class NotificationEvent(id: String, event: ProcessChangeEvent, date: Instant, user: User)

