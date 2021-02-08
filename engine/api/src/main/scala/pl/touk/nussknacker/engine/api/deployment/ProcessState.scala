package pl.touk.nussknacker.engine.api.deployment
import java.net.URI
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

//@TODO: In future clean up it.
trait ProcessStateDefinitionManager {
  def statusActions(stateStatus: StateStatus): List[ProcessActionType]
  def statusTooltip(stateStatus: StateStatus): Option[String]
  def statusDescription(stateStatus: StateStatus): Option[String]
  def statusIcon(stateStatus: StateStatus): Option[URI]
  //Temporary mapping ProcessActionType to StateStatus. TODO: Remove it when we will support state cache
  def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus
}

object ProcessState {
  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap(_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder.decodeString.map(URI.create)

  def apply(deploymentId: String, status: StateStatus, version: Option[ProcessVersion], definitionManager: ProcessStateDefinitionManager): ProcessState =
    ProcessState(Some(DeploymentId(deploymentId)), status, version, definitionManager, Option.empty, Option.empty, List.empty)

  def apply(deploymentId: Option[DeploymentId],
            status: StateStatus,
            version: Option[ProcessVersion],
            definitionManager: ProcessStateDefinitionManager,
            startTime: Option[Long],
            attributes: Option[Json],
            errors: List[String]): ProcessState =
    ProcessState(
      deploymentId,
      status,
      version,
      definitionManager.statusActions(status),
      definitionManager.statusIcon(status),
      definitionManager.statusTooltip(status),
      definitionManager.statusDescription(status),
      startTime,
      attributes,
      errors
    )

}

@JsonCodec case class ProcessState(deploymentId: Option[DeploymentId],
                                   status: StateStatus,
                                   version: Option[ProcessVersion],
                                   allowedActions: List[ProcessActionType],
                                   icon: Option[URI],
                                   tooltip: Option[String],
                                   description: Option[String],
                                   startTime: Option[Long],
                                   attributes: Option[Json],
                                   errors: List[String]) {
  def isDeployed: Boolean = status.isRunning || status.isDuringDeploy
}

object ProcessActionType extends Enumeration {
  implicit val typeEncoder: Encoder[ProcessActionType.Value] = Encoder.enumEncoder(ProcessActionType)
  implicit val typeDecoder: Decoder[ProcessActionType.Value] = Decoder.enumDecoder(ProcessActionType)

  type ProcessActionType = Value
  val Deploy: Value = Value("DEPLOY")
  val Cancel: Value = Value("CANCEL")
  val Archive: Value = Value("ARCHIVE")
  val UnArchive: Value = Value("UNARCHIVE")
  val Pause: Value = Value("PAUSE") //TODO: To implement in future..
}

object StateStatus {
  implicit val configuration: Configuration = Configuration
    .default
    .withDefaults
    .withDiscriminator("type")
}

@ConfiguredJsonCodec sealed trait StateStatus {
  //used for filtering processes (e.g. shouldBeRunning)
  def isDuringDeploy: Boolean = false
  //used for handling finished
  def isFinished: Boolean = false

  def isRunning: Boolean = false
  def isFailed: Boolean = false
  def name: String

}

final case class AllowDeployStateStatus(name: String) extends StateStatus

final case class NotEstablishedStateStatus(name: String) extends StateStatus

final case class DuringDeployStateStatus(name: String) extends StateStatus {
  override def isDuringDeploy: Boolean = true
}

final case class FinishedStateStatus(name: String) extends StateStatus {
  override def isFinished: Boolean = true
}

final case class RunningStateStatus(name: String) extends StateStatus {
  override def isRunning: Boolean = true
}

final case class FailedStateStatus(name: String) extends StateStatus {
  override def isFailed: Boolean = true
}

// This status class is a walk around for fact that StateStatus is encoded and decoded. It causes that there is no easy option
// to add own status with some specific fields without passing Encoders and Decoders to many places in application.
// TODO: we should find places where StateStatuses are encoded and decoded and replace them with some DTOs for this purpose
class CustomStateStatus(val name: String) extends StateStatus
