package pl.touk.nussknacker.ui.notifications

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.notifications.DataToRefresh.DataToRefresh

@JsonCodec case class Notification(id: String,
                                   scenarioName: Option[ProcessName],
                                   message: String,
                                   //none is marker notification, just to refresh the data
                                   `type`: Option[NotificationType.Value],
                                   toRefresh: List[DataToRefresh])

object Notification {
  def deploymentActionFailedNotification(id: String, actionType: ProcessActionType, name: ProcessName, failureMessageOpt: Option[String]): Notification = {
    Notification(id, Some(name), s"${displayableActionName(actionType)} of ${name.value} failed" + failureMessageOpt.map(" with reason: " + _).getOrElse(""),
      Some(NotificationType.error), List(DataToRefresh.state))
  }

  def deploymentActionFinishedNotification(id: String, actionType: ProcessActionType, name: ProcessName): Notification = {
    //We don't want to display this notification, not to confuse user, as
    //deployment may proceed asynchronously (e.g. in streaming-lite)
    Notification(id, Some(name), s"${displayableActionName(actionType)} finished", None, List(DataToRefresh.versions, DataToRefresh.activity, DataToRefresh.state))
  }

  private def displayableActionName(actionType: ProcessActionType): String =
    actionType match {
      case ProcessActionType.Deploy => "Deployment"
      case ProcessActionType.Cancel => "Cancel"
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
  val versions, activity, state = Value
}