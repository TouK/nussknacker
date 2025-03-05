package pl.touk.nussknacker.ui.notifications

import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.notifications.NotificationService.NotificationsScope
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, ScenarioActionRepository}
import pl.touk.nussknacker.ui.process.scenarioactivity.FetchScenarioActivityService
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.InMemoryTimeseriesRepository

import java.time.{Clock, Instant}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

final case class NotificationConfig(duration: FiniteDuration)

trait NotificationService {

  def notifications(scope: NotificationsScope)(
      implicit ec: ExecutionContext
  ): Future[List[Notification]]

}

object NotificationService {

  sealed trait NotificationsScope

  object NotificationsScope {

    final case class NotificationsForLoggedUser(
        user: LoggedUser,
    ) extends NotificationsScope

    final case class NotificationsForLoggedUserAndScenario(
        user: LoggedUser,
        processName: ProcessName,
    ) extends NotificationsScope

  }

}

class NotificationServiceImpl(
    fetchScenarioActivityService: FetchScenarioActivityService,
    scenarioActionRepository: ScenarioActionRepository,
    globalNotificationRepository: InMemoryTimeseriesRepository[Notification],
    dbioRunner: DBIOActionRunner,
    config: NotificationConfig,
    clock: Clock = Clock.systemUTC()
) extends NotificationService {

  override def notifications(
      scope: NotificationsScope,
  )(implicit ec: ExecutionContext): Future[List[Notification]] = {
    val now   = clock.instant()
    val limit = now.minusMillis(config.duration.toMillis)
    def fetchUserAndGlobalNotifications(user: LoggedUser) =
      for {
        notificationsForUserActions <- notificationsForUserActions(user, limit)
        globalNotifications = fetchGlobalNotificationsAndTriggerEviction(limit)
      } yield notificationsForUserActions ++ globalNotifications
    scope match {
      case NotificationsScope.NotificationsForLoggedUser(user) =>
        fetchUserAndGlobalNotifications(user)
      case NotificationsScope.NotificationsForLoggedUserAndScenario(user, processName) =>
        for {
          userAndGlobalNotifications         <- fetchUserAndGlobalNotifications(user)
          notificationsForScenarioActivities <- notificationsForScenarioActivities(user, processName, limit)
        } yield userAndGlobalNotifications ++ notificationsForScenarioActivities
    }

  }

  private def notificationsForUserActions(user: LoggedUser, limit: Instant)(
      implicit ec: ExecutionContext
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

  private def notificationsForScenarioActivities(user: LoggedUser, processName: ProcessName, limit: Instant)(
      implicit ec: ExecutionContext
  ): Future[List[Notification]] = {
    for {
      allActivities <- fetchScenarioActivityService.fetchActivities(processName, Some(limit))(user).value.map {
        case Right(activities) => activities
        case Left(_)           => List.empty
      }
      notificationsForScenarioActivities = allActivities.map { activity =>
        Notification.scenarioStateUpdateNotification(activity, processName)
      }
    } yield notificationsForScenarioActivities
  }

  private def fetchGlobalNotificationsAndTriggerEviction(limit: Instant) = {
    val globalNotifications = globalNotificationRepository.fetchEntries(limit)
    globalNotificationRepository.evictOldEntries()
    globalNotifications
  }

}
