package pl.touk.nussknacker.engine.api.deployment

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}

import java.time.Instant
import java.util.UUID

@JsonCodec case class ProcessAction(id: ProcessActionId,
                                    processId: ProcessId,
                                    // We use process action only for finished actions so processVersionId is always defined
                                    processVersionId: VersionId,
                                    user: String,
                                    createdAt: Instant,
                                    // We use process action only for finished actions so performedAt is always defined
                                    performedAt: Instant,
                                    actionType: ProcessActionType,
                                    state: ProcessActionState,
                                    failureMessage: Option[String],
                                    commentId: Option[Long],
                                    comment: Option[String],
                                    buildInfo: Map[String, String])

final case class ProcessActionId(value: UUID) {
  override def toString: String = value.toString
}

object ProcessActionId {

  implicit val typeEncoder: Encoder[ProcessActionId] = Encoder.encodeUUID.contramap(_.value)
  implicit val typeDecoder: Decoder[ProcessActionId] = Decoder.decodeUUID.map(ProcessActionId(_))

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
  val Rename: Value = Value("RENAME")

  val DefaultActions: List[ProcessActionType] = Nil
}

object ProcessActionState extends Enumeration {
  implicit val typeEncoder: Encoder[ProcessActionState.Value] = Encoder.encodeEnumeration(ProcessActionState)
  implicit val typeDecoder: Decoder[ProcessActionState.Value] = Decoder.decodeEnumeration(ProcessActionState)

  type ProcessActionState = Value
  val InProgress: Value = Value("IN_PROGRESS")
  val Finished: Value = Value("FINISHED")
  val Failed: Value = Value("FAILED")
}
