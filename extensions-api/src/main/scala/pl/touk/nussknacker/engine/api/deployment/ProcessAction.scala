package pl.touk.nussknacker.engine.api.deployment

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor}
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.deriveEncoder
import pl.touk.nussknacker.engine.api.deployment
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}

import java.time.Instant
import java.util.UUID
import scala.util.Random

case class ProcessAction(id: ProcessActionId,
                                    processId: ProcessId,
                                    // We use process action only for finished/execution finished actions so processVersionId is always defined
                                    processVersionId: VersionId,
                                    user: String,
                                    createdAt: Instant,
                                    // We use process action only for finished/execution finished actions so performedAt is always defined
                                    performedAt: Instant,
                                    actionType: ProcessActionType,
                                    state: ProcessActionState,
                                    failureMessage: Option[String],
                                    commentId: Option[Long],
                                    comment: Option[String],
                                    buildInfo: Map[String, String])

//TODO  remove it in NU 1.12 and restore @JsonCodec
object ProcessAction {
  //custom decoder for compatibility reasons
  implicit val decodeProcessAction: Decoder[ProcessAction] = new Decoder[ProcessAction] {
    override def apply(c: HCursor): Result[ProcessAction] =
      for {
        id               <- c.downField("id").as[ProcessActionId] match {
                              case Left(_) => Right(ProcessActionId(new UUID(0, 0)))
                              case Right(id) => Right(id)
                            }
        processId        <- c.downField("processId").as[ProcessId] match {
                              case Left(_) => Right(ProcessId(0L))
                              case Right(processId) => Right(processId)
                            }
        processVersionId <- c.downField("processVersionId").as[VersionId]
        user             <- c.downField("user").as[String]
        createdAt        <- c.downField("createdAt").as[Instant] match {
                              case Left(_) => Right(Instant.now())
                              case Right(createdAt) => Right(createdAt)
                            }
        performedAt      <- c.downField("performedAt").as[Instant]
        actionType       <- c.downField("actionType").as[ProcessActionType] match {
                              case Left(_) => c.downField("action").as[ProcessActionType]
                              case Right(actionType) => Right(actionType)
                            }
        state            <- c.downField("state").as[ProcessActionState] match {
                              case Left(_) => Right(ProcessActionState.InProgress)
                              case Right(state) => Right(state)
                            }
        failureMessage   <- c.downField("failureMessage").as[Option[String]] match {
                              case Left(_) => Right(None)
                              case Right(failureMessage) => Right(failureMessage)
                            }
        commentId        <- c.downField("commentId").as[Option[Long]]
        comment          <- c.downField("comment").as[Option[String]]
        buildInfo        <- c.downField("buildInfo").as[Map[String, String]]
      } yield ProcessAction(id, processId, processVersionId, user, createdAt, performedAt, actionType, state, failureMessage, commentId, comment, buildInfo)
  }

  implicit val encodeProcessAction: Encoder[ProcessAction] = deriveEncoder[ProcessAction]
}

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
  // This is a special marker state for long running actions which means that action execution is finished
  // (not action request is finished but the whole execution is)
  val ExecutionFinished: Value = Value("EXECUTION_FINISHED")

  val FinishedStates: Set[ProcessActionState] = Set(Finished, ExecutionFinished)
}
