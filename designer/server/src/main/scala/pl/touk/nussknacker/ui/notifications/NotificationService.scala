package pl.touk.nussknacker.ui.notifications

import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, ScenarioActionRepository}
import pl.touk.nussknacker.ui.process.scenarioactivity.ScenarioActivityService
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ScenarioActivityUtils.ScenarioActivityOps

import java.time.{Clock, Instant}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

final case class NotificationConfig(duration: FiniteDuration)

trait NotificationService {

  def notifications(processName: Option[ProcessName])(
      implicit user: LoggedUser,
      ec: ExecutionContext
  ): Future[List[Notification]]

}

class NotificationServiceImpl(
    scenarioActivityService: ScenarioActivityService,
    scenarioActionRepository: ScenarioActionRepository,
    dbioRunner: DBIOActionRunner,
    config: NotificationConfig,
    clock: Clock = Clock.systemUTC()
) extends NotificationService {

  override def notifications(
      processNameOpt: Option[ProcessName]
  )(implicit user: LoggedUser, ec: ExecutionContext): Future[List[Notification]] = {
    val now   = clock.instant()
    val limit = now.minusMillis(config.duration.toMillis)
    processNameOpt match {
      case Some(processName) =>
        for {
          notificationsForUserActions        <- notificationsForUserActions(limit)
          notificationsForScenarioActivities <- notificationsForScenarioActivities(processName, limit)
        } yield notificationsForUserActions ++ notificationsForScenarioActivities
      case None =>
        notificationsForUserActions(limit)
    }

  }

  private def notificationsForUserActions(limit: Instant)(
      implicit user: LoggedUser,
      ec: ExecutionContext
  ): Future[List[Notification]] = dbioRunner.run {
    scenarioActionRepository
      .getUserActionsAfter(
        user,
        Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel),
        ProcessActionState.FinishedStates + ProcessActionState.Failed,
        limit
      )
      .map(_.map { case (action, scenarioName) =>
        action.state match {
          case ProcessActionState.Finished =>
            Notification.actionFinishedNotification(action.id.toString, action.actionName, scenarioName)
          case ProcessActionState.Failed =>
            Notification
              .actionFailedNotification(action.id.toString, action.actionName, scenarioName, action.failureMessage)
          case ProcessActionState.ExecutionFinished =>
            Notification
              .actionExecutionFinishedNotification(action.id.toString, action.actionName, scenarioName)
          case ProcessActionState.InProgress =>
            throw new IllegalStateException(
              s"Unexpected action returned by query: $action, for scenario: $scenarioName"
            )
        }
      })
  }

  private def notificationsForScenarioActivities(processName: ProcessName, limit: Instant)(
      implicit user: LoggedUser,
      ec: ExecutionContext
  ): Future[List[Notification]] = {
    for {
      allActivities <- scenarioActivityService.fetchActivities(processName, Some(limit)).value.map {
        case Right(activities) => activities
        case Left(_)           => List.empty
      }
      notificationsForScenarioActivities = allActivities.map { activity =>
        Notification.scenarioStateUpdateNotification(
          s"${activity.scenarioActivityId.value.toString}_${activity.lastModifiedAt.toEpochMilli}",
          activity.activityType.entryName,
          processName
        )
      }
    } yield notificationsForScenarioActivities
  }

}
