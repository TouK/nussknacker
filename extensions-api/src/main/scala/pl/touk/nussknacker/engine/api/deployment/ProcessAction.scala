package pl.touk.nussknacker.engine.api.deployment

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.process.VersionId

import java.time.Instant

@JsonCodec case class ProcessAction(processVersionId: VersionId,
                                    performedAt: Instant,
                                    user: String,
                                    action: ProcessActionType,
                                    commentId: Option[Long],
                                    comment: Option[String],
                                    buildInfo: Map[String, String]) {
  def isDeployed: Boolean = action.equals(ProcessActionType.Deploy)
  def isCanceled: Boolean = action.equals(ProcessActionType.Cancel)
}

object ProcessActionType extends Enumeration {
  implicit val typeEncoder: Encoder[ProcessActionType.Value] = Encoder.encodeEnumeration(ProcessActionType)
  implicit val typeDecoder: Decoder[ProcessActionType.Value] = Decoder.decodeEnumeration(ProcessActionType)

  type ProcessActionType = Value
  val Deploy: Value = Value("DEPLOY")
  val Cancel: Value = Value("CANCEL")
  val Archive: Value = Value("ARCHIVE")
  val UnArchive: Value = Value("UNARCHIVE")
  val Pause: Value = Value("PAUSE") //TODO: To implement in future..

  val defaultActions: List[ProcessActionType] = Nil
}

object ProcessActionState extends Enumeration {
  implicit val typeEncoder: Encoder[ProcessActionState.Value] = Encoder.encodeEnumeration(ProcessActionState)
  implicit val typeDecoder: Decoder[ProcessActionState.Value] = Decoder.decodeEnumeration(ProcessActionState)

  type ProcessActionState = Value
  val InProgress: Value = Value("IN_PROGRESS")
  val Finished: Value = Value("FINISHED")
  val Failed: Value = Value("FAILED")
}
