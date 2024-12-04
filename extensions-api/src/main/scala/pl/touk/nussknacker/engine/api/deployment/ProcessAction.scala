package pl.touk.nussknacker.engine.api.deployment

import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}

import java.time.Instant
import java.util.UUID

// todo NU-1772
//  - should be eventually replaced with pl.touk.nussknacker.engine.api.deployment.ScenarioActivity
//  - this class is currently a compatibility layer for older fragments of code, new code should use ScenarioActivity
@JsonCodec case class ProcessAction(
    id: ProcessActionId,
    processId: ProcessId,
    // We use process action only for finished/execution finished actions so processVersionId is always defined
    processVersionId: VersionId,
    user: String,
    createdAt: Instant,
    // We use process action only for finished/execution finished actions so performedAt is always defined
    performedAt: Instant,
    actionName: ScenarioActionName,
    state: ProcessActionState,
    failureMessage: Option[String],
    commentId: Option[Long],
    comment: Option[String],
    buildInfo: Map[String, String]
)

final case class ProcessActionId(value: UUID) {
  override def toString: String = value.toString
}

object ProcessActionId {

  implicit val typeEncoder: Encoder[ProcessActionId] = Encoder.encodeUUID.contramap(_.value)
  implicit val typeDecoder: Decoder[ProcessActionId] = Decoder.decodeUUID.map(ProcessActionId(_))

}

object ProcessActionState extends Enumeration {
  implicit val typeEncoder: Encoder[ProcessActionState.Value] = Encoder.encodeEnumeration(ProcessActionState)
  implicit val typeDecoder: Decoder[ProcessActionState.Value] = Decoder.decodeEnumeration(ProcessActionState)

  type ProcessActionState = Value
  val InProgress: Value = Value("IN_PROGRESS")
  val Finished: Value   = Value("FINISHED")
  val Failed: Value     = Value("FAILED")
  // This is a special marker state for long running actions which means that action execution is finished
  // (not action request is finished but the whole execution is)
  val ExecutionFinished: Value = Value("EXECUTION_FINISHED")

  val FinishedStates: Set[ProcessActionState] = Set(Finished, ExecutionFinished)
}

final case class ScenarioActionName(value: String) extends AnyVal {
  override def toString: String = value
}

object ScenarioActionName {

  implicit val encoder: Encoder[ScenarioActionName] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[ScenarioActionName] = deriveUnwrappedDecoder

  val Deploy: ScenarioActionName    = ScenarioActionName("DEPLOY")
  val Cancel: ScenarioActionName    = ScenarioActionName("CANCEL")
  val Archive: ScenarioActionName   = ScenarioActionName("ARCHIVE")
  val UnArchive: ScenarioActionName = ScenarioActionName("UNARCHIVE")
  val Pause: ScenarioActionName     = ScenarioActionName("PAUSE") // TODO: To implement in future..
  val Rename: ScenarioActionName    = ScenarioActionName("RENAME")

  val DefaultActions: List[ScenarioActionName] = Nil

  val StateActions: Set[ScenarioActionName] = Set(Cancel, Deploy, Pause)

  // TODO: We kept the old name of "run now" CustomAction for compatibility reasons.
  //       In the future it can be changed to better name, according to convention, but that would require database migration
  //       In the meantime, there are methods serialize and deserialize, which operate on name PERFORM_SINGLE_EXECUTION instead.
  val PerformSingleExecution: ScenarioActionName = ScenarioActionName("run now")

  def serialize(name: ScenarioActionName): String = name match {
    case ScenarioActionName.PerformSingleExecution => "PERFORM_SINGLE_EXECUTION"
    case other                                     => other.value
  }

  def deserialize(str: String): ScenarioActionName = str match {
    case "PERFORM_SINGLE_EXECUTION" => ScenarioActionName.PerformSingleExecution
    case other                      => ScenarioActionName(other)
  }

}
