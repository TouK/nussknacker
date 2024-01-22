package pl.touk.nussknacker.ui.notifications

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.ui.db.entity.ProcessActionEntityData
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, DbProcessActionRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.{Clock, Instant}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

final case class NotificationConfig(duration: FiniteDuration)

trait NotificationService {

  def notifications(implicit user: LoggedUser, ec: ExecutionContext): Future[List[Notification]]

}

class NotificationServiceImpl(
    actionRepository: DbProcessActionRepository,
    dbioRunner: DBIOActionRunner,
    config: NotificationConfig,
    clock: Clock = Clock.systemUTC()
) extends NotificationService {

  override def notifications(implicit user: LoggedUser, ec: ExecutionContext): Future[List[Notification]] = {
    val now   = clock.instant()
    val limit = now.minusMillis(config.duration.toMillis)
    dbioRunner
      .run(
        actionRepository.getUserActionsAfter(
          user,
          Set(ProcessActionType.Deploy, ProcessActionType.Cancel),
          ProcessActionState.FinishedStates + ProcessActionState.Failed,
          limit
        )
      )
      .map(_.map {
        case (
              ProcessActionEntityData(id, _, _, _, _, _, actionType, ProcessActionState.Finished, _, _, _),
              processName
            ) =>
          Notification.actionFinishedNotification(id.toString, actionType, processName)
        case (
              ProcessActionEntityData(id, _, _, _, _, _, actionType, ProcessActionState.ExecutionFinished, _, _, _),
              processName
            ) =>
          Notification.actionExecutionFinishedNotification(id.toString, actionType, processName)
        case (
              ProcessActionEntityData(
                id,
                _,
                _,
                _,
                _,
                _,
                actionType,
                ProcessActionState.Failed,
                failureMessageOpt,
                _,
                _
              ),
              processName
            ) =>
          Notification.actionFailedNotification(id.toString, actionType, processName, failureMessageOpt)
        case (a, processName) =>
          throw new IllegalStateException(s"Unexpected action returned by query: $a, for scenario: $processName")
      }.toList)
  }

}
