package pl.touk.nussknacker.ui.notifications

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.ui.db.entity.ProcessActionEntityData
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, DbProcessActionRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.{Clock, Instant}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

case class NotificationConfig(duration: FiniteDuration)

trait NotificationService {
  def notifications(notificationsAfter: Option[Instant])(implicit user: LoggedUser, ec: ExecutionContext): Future[List[Notification]]

}

class NotificationServiceImpl(actionRepository: DbProcessActionRepository[DB], dbioRunner: DBIOActionRunner,
                              config: NotificationConfig, clock: Clock = Clock.systemUTC()) extends NotificationService {
  override def notifications(notificationsAfter: Option[Instant])(implicit user: LoggedUser, ec: ExecutionContext): Future[List[Notification]] = {
    val now = clock.instant()
    val limitBasedOnConfig = now.minusMillis(config.duration.toMillis)
    def maxInstant(instant1: Instant, instant2: Instant) = if (instant1.compareTo(instant2) > 0) instant1 else instant2
    val limit = notificationsAfter.map(maxInstant(_, limitBasedOnConfig)).getOrElse(limitBasedOnConfig)
    dbioRunner.run(actionRepository.getUserActionsAfter(user,
      Set(ProcessActionState.Failed, ProcessActionState.Finished),
      Set(ProcessActionType.Deploy, ProcessActionType.Cancel), limit)).map(_.map {
      case (ProcessActionEntityData(id, _, _, _, _, _, actionType, ProcessActionState.Finished, _, _, _), processName) =>
        Notification.deploymentActionFinishedNotification(id.toString, actionType, processName)
      case (ProcessActionEntityData(id, _, _, _, _, _, actionType, ProcessActionState.Failed, failureMessageOpt, _, _), processName) =>
        Notification.deploymentActionFailedNotification(id.toString, actionType, processName, failureMessageOpt)
      case (a, processName) =>
        throw new IllegalStateException(s"Unexpected action returned by query: $a, for scenario: $processName")
    }.toList)
  }
}