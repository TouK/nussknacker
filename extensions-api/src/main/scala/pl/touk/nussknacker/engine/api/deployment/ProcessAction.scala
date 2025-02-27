package pl.touk.nussknacker.engine.api.deployment

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}

import java.time.Instant
import java.util.UUID

// TODO: NU-1772
//  - should be eventually replaced with pl.touk.nussknacker.engine.api.deployment.ScenarioActivity
//  - this class is currently a compatibility layer for older fragments of code, new code should use ScenarioActivity
//  - this class is used in REST API (JsonCodec) only by external project
@JsonCodec case class ProcessAction(
    id: ProcessActionId,
    // Used by external project (listener api)
    processId: ProcessId,
    // Used by external project
    // We use process action only for finished/execution finished actions so processVersionId is always defined
    override val processVersionId: VersionId,
    override val user: String,
    // We use process action only for finished/execution finished actions so performedAt is always defined
    // Used by external project
    performedAt: Instant,
    // Used by external project
    override val actionName: ScenarioActionName,
    override val state: ProcessActionState,
    failureMessage: Option[String],
    // Used by external project
    comment: Option[String],
) extends ScenarioStatusActionDetails

// This is the narrowest set of information required by scenario status resolving mechanism.
trait ScenarioStatusActionDetails {
  def actionName: ScenarioActionName
  def state: ProcessActionState
  def processVersionId: VersionId
  def user: String
}

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

  implicit val keyEncoder: KeyEncoder[ScenarioActionName] = KeyEncoder.encodeKeyString.contramap(_.value)
  implicit val keyDecoder: KeyDecoder[ScenarioActionName] = KeyDecoder.decodeKeyString.map(ScenarioActionName(_))

  val Deploy: ScenarioActionName    = ScenarioActionName("DEPLOY")
  val Cancel: ScenarioActionName    = ScenarioActionName("CANCEL")
  val Archive: ScenarioActionName   = ScenarioActionName("ARCHIVE")
  val UnArchive: ScenarioActionName = ScenarioActionName("UNARCHIVE")
  // TODO remove unused action
  val Pause: ScenarioActionName  = ScenarioActionName("PAUSE") // TODO: To implement in future..
  val Rename: ScenarioActionName = ScenarioActionName("RENAME")
  // TODO: We kept the old name of "run now" CustomAction for compatibility reasons.
  //       In the future it can be changed to better name, according to convention, but that would require database migration
  //       In the meantime, there are methods serialize and deserialize, which operate on name RUN_OFF_SCHEDULE instead.
  val RunOffSchedule: ScenarioActionName = ScenarioActionName("run now")

  val DefaultActions: List[ScenarioActionName] = Nil

  val ScenarioStatusActions: Set[ScenarioActionName] = Set(Cancel, Deploy)

  def serialize(name: ScenarioActionName): String = name match {
    case ScenarioActionName.RunOffSchedule => "RUN_OFF_SCHEDULE"
    case other                             => other.value
  }

  def deserialize(str: String): ScenarioActionName = str match {
    case "RUN_OFF_SCHEDULE" => ScenarioActionName.RunOffSchedule
    case other              => ScenarioActionName(other)
  }

}

/**
 * Used to define Flink deployment parameters for each action
 */
trait WithActionParametersSupport {
  def actionParametersDefinition: Map[ScenarioActionName, Map[ParameterName, ParameterConfig]]
}
