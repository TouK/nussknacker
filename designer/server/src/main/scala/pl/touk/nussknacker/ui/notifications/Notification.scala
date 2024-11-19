package pl.touk.nussknacker.ui.notifications

import derevo.circe.{decoder, encoder}
import derevo.derive
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionState, ScenarioActionName}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.notifications.DataToRefresh.DataToRefresh
import sttp.tapir.Schema
import sttp.tapir.derevo.schema

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
      id: String,
      activityName: String,
      name: ProcessName
  ): Notification = {
    Notification(
      id = id,
      scenarioName = Some(name),
      message = activityName,
      `type` = None,
      toRefresh = List(DataToRefresh.activity, DataToRefresh.state)
    )
  }

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
  val activity, state = Value
}
