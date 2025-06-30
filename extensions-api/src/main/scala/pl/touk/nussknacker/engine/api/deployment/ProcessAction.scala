package pl.touk.nussknacker.engine.api.deployment

import enumeratum._
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.StaticParameterConfig
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}

import java.time.Instant
import java.util.UUID
import scala.collection.immutable

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

sealed abstract class ScenarioActionName(override val entryName: String) extends EnumEntry {
  def value: String             = entryName
  override def toString: String = value
}

object ScenarioActionName extends Enum[ScenarioActionName] {

  implicit val encoder: Encoder[ScenarioActionName] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[ScenarioActionName] =
    Decoder.decodeString.emap(ScenarioActionName.withNameEither(_).left.map(_.getMessage()))

  implicit val keyEncoder: KeyEncoder[ScenarioActionName] = KeyEncoder.encodeKeyString.contramap(_.value)
  implicit val keyDecoder: KeyDecoder[ScenarioActionName] = KeyDecoder.decodeKeyString.map(ScenarioActionName.withName)

  case object Deploy    extends ScenarioActionName("DEPLOY")
  case object Redeploy  extends ScenarioActionName("REDEPLOY")
  case object Cancel    extends ScenarioActionName("CANCEL")
  case object Archive   extends ScenarioActionName("ARCHIVE")
  case object UnArchive extends ScenarioActionName("UNARCHIVE")
  // TODO remove unused action
  case object Pause  extends ScenarioActionName("PAUSE") // TODO: To implement in future..
  case object Rename extends ScenarioActionName("RENAME")
  // TODO: We kept the old name of "run now" CustomAction for compatibility reasons.
  //       In the future it can be changed to better name, according to convention, but that would require database migration
  //       In the meantime, there are methods serialize and deserialize, which operate on name RUN_OFF_SCHEDULE instead.
  case object RunOffSchedule extends ScenarioActionName("run now") // Compatibility note

  val DefaultActions: Set[ScenarioActionName] = Set.empty

  val ScenarioStatusActions: Set[ScenarioActionName] = Set(Cancel, Deploy, Redeploy)

  def serialize(name: ScenarioActionName): String = name match {
    case ScenarioActionName.RunOffSchedule => "RUN_OFF_SCHEDULE"
    case other                             => other.value
  }

  def deserialize(str: String): ScenarioActionName = str match {
    case "RUN_OFF_SCHEDULE" => ScenarioActionName.RunOffSchedule
    case other              => ScenarioActionName.withName(other)
  }

  override def values: immutable.IndexedSeq[ScenarioActionName] = findValues

}

/**
 * Used to define Flink parameters for each action
 */
trait WithActionParametersSupport {
  def actionParametersDefinition: Map[ScenarioActionName, Map[ParameterName, StaticParameterConfig]]
}
