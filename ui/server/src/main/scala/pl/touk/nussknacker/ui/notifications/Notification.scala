package pl.touk.nussknacker.ui.notifications

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.ui.notifications.NotificationAction.NotificationAction

@JsonCodec case class Notification(id: String, message: String, `type`: NotificationType.Value, action: Option[NotificationAction])

object NotificationType extends Enumeration {

  implicit val typeEncoder: Encoder[NotificationType.Value] = Encoder.encodeEnumeration(NotificationType)
  implicit val typeDecoder: Decoder[NotificationType.Value] = Decoder.decodeEnumeration(NotificationType)

  type NotificationType = Value
  val info, warning = Value
}


object NotificationAction extends Enumeration {

  implicit val typeEncoder: Encoder[NotificationAction.Value] = Encoder.encodeEnumeration(NotificationAction)
  implicit val typeDecoder: Decoder[NotificationAction.Value] = Decoder.decodeEnumeration(NotificationAction)

  type NotificationAction = Value
  val deploymentFailed, deploymentFinished = Value
}