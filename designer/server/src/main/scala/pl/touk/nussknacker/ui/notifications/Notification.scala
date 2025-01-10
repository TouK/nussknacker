package pl.touk.nussknacker.ui.notifications

import derevo.circe.{decoder, encoder}
import derevo.derive
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.deployment.{
  DeploymentRelatedActivity,
  ProcessActionState,
  ScenarioActionName,
  ScenarioActivity
}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.notifications.DataToRefresh.DataToRefresh
import pl.touk.nussknacker.ui.util.ScenarioActivityUtils.ScenarioActivityOps
import sttp.tapir.Schema
import sttp.tapir.derevo.schema

import java.util.UUID

@derive(encoder, decoder, schema)
final case class Notification(
    id: String,
    scenarioName: Option[ProcessName],
    message: String,
    // none is marker notification, just to refresh the data
    `type`: Option[NotificationType.Value],
    toRefresh: List[DataToRefresh]
)

object Notification {

  implicit val processNameSchema: Schema[ProcessName] =
    Schema.string

  def actionFailedNotification(
      id: String,
      actionName: ScenarioActionName,
      name: ProcessName,
      failureMessageOpt: Option[String]
  ): Notification = {
    Notification(
      id,
      Some(name),
      s"${displayableActionName(actionName)} of $name failed" + failureMessageOpt
        .map(" with reason: " + _)
        .getOrElse(""),
      Some(NotificationType.error),
      List(DataToRefresh.state)
    )
  }

  def actionFinishedNotification(id: String, actionName: ScenarioActionName, name: ProcessName): Notification = {
    // We don't want to display this notification, because user already see that status icon was changed
    Notification(
      id,
      Some(name),
      s"${displayableActionName(actionName)} finished",
      None,
      List(DataToRefresh.activity, DataToRefresh.state)
    )
  }

  def actionExecutionFinishedNotification(
      id: String,
      actionName: ScenarioActionName,
      name: ProcessName
  ): Notification = {
    // We don't want to display this notification, because user already see that status icon was changed
    // Also we want to distinguish between finished and execution finished to make sure that refresh will happen after execution finished
    Notification(
      id + "-" + ProcessActionState.ExecutionFinished,
      Some(name),
      s"${displayableActionName(actionName)} execution finished",
      None,
      List(DataToRefresh.activity, DataToRefresh.state)
    )
  }

  def scenarioStateUpdateNotification(
      activity: ScenarioActivity,
      name: ProcessName
  ): Notification = {
    val toRefresh = activity match {
      case _: DeploymentRelatedActivity => List(DataToRefresh.activity, DataToRefresh.state)
      case _                            => List(DataToRefresh.activity)
    }
    Notification(
      id = s"${activity.scenarioActivityId.value.toString}_${activity.lastModifiedAt.toEpochMilli}",
      scenarioName = Some(name),
      message = activity.activityType.entryName,
      // We don't want to display this notification, because it causes the activities toolbar to refresh
      `type` = None,
      toRefresh = toRefresh
    )
  }

  def configurationReloaded: Notification =
    Notification(
      id = UUID.randomUUID().toString,
      scenarioName = None,
      message = "Configuration reloaded",
      // We don't want to show this notification to other users because they might be not interested, and it can only introduce a confusion
      `type` = None,
      toRefresh = List(DataToRefresh.creator)
    )

  private def displayableActionName(actionName: ScenarioActionName): String =
    actionName match {
      case ScenarioActionName.Deploy => "Deployment"
      case ScenarioActionName.Cancel => "Cancel"
      case _                         => throw new AssertionError(s"Not supported deployment action: $actionName")
    }

}

object NotificationType extends Enumeration {

  implicit val typeEncoder: Encoder[NotificationType.Value] = Encoder.encodeEnumeration(NotificationType)
  implicit val typeDecoder: Decoder[NotificationType.Value] = Decoder.decodeEnumeration(NotificationType)

  type NotificationType = Value
  val info, success, error = Value
}

object DataToRefresh extends Enumeration {

  implicit val typeEncoder: Encoder[DataToRefresh.Value] = Encoder.encodeEnumeration(DataToRefresh)
  implicit val typeDecoder: Decoder[DataToRefresh.Value] = Decoder.decodeEnumeration(DataToRefresh)

  type DataToRefresh = Value
  val activity, state, creator = Value
}
