package pl.touk.nussknacker.ui.notifications

import pl.touk.nussknacker.engine.api.deployment.{ProcessActionState, ScenarioActionName}
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, ScenarioActionRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.Clock
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

final case class NotificationConfig(duration: FiniteDuration)

trait NotificationService {

  def notifications(implicit user: LoggedUser, ec: ExecutionContext): Future[List[Notification]]

}

class NotificationServiceImpl(
    scenarioActionRepository: ScenarioActionRepository,
    dbioRunner: DBIOActionRunner,
    config: NotificationConfig,
    clock: Clock = Clock.systemUTC()
) extends NotificationService {

  override def notifications(implicit user: LoggedUser, ec: ExecutionContext): Future[List[Notification]] = {
    val now   = clock.instant()
    val limit = now.minusMillis(config.duration.toMillis)
    dbioRunner
      .run(
        scenarioActionRepository.getUserActionsAfter(
          user,
          Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel),
          ProcessActionState.FinishedStates + ProcessActionState.Failed,
          limit
        )
      )
      .map(_.map { case (action, scenarioName) =>
        action.state match {
          case ProcessActionState.Finished =>
            Notification
              .actionFinishedNotification(action.id.toString, action.actionName, scenarioName)
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

}
