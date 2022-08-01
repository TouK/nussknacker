package pl.touk.nussknacker.ui.notifications

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.notifications.DataToRefresh.DataToRefresh

@JsonCodec case class Notification(id: String,
                                   scenarioName: Option[ProcessName],
                                   message: String,
                                   //none is marker notification, just to refresh the data
                                   `type`: Option[NotificationType.Value],
                                   toRefresh: List[DataToRefresh])

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