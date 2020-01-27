package pl.touk.nussknacker.engine.api.deployment
import java.net.URI

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType

trait ProcessStateDefinitionManager {
  def statusActions(stateStatus: StateStatus): List[ProcessActionType]
  def statusTooltip(stateStatus: StateStatus): Option[String]
  def statusMessage(stateStatus: StateStatus): Option[String]
  def statusIcon(stateStatus: StateStatus): Option[URI]
  def statusName(stateStatus: StateStatus): String
  //Temporary mapping ProcessActionType to StateStatus. TODO: Remove it when we will support state cache
  def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus
}

object ProcessState {
  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap(_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder.decodeString.map(URI.create)

  def apply(deploymentId: String, status: StateStatus, version: Option[ProcessVersion], definitionManager: ProcessStateDefinitionManager): ProcessState =
    ProcessState(DeploymentId(deploymentId), status, version, definitionManager, Option.empty, Option.empty, Option.empty)

  def apply(deploymentId: DeploymentId,
            status: StateStatus,
            processVersionId: Option[ProcessVersion],
            definitionManager: ProcessStateDefinitionManager,
            startTime: Option[Long],
            attributes: Option[Json],
            errors: Option[String]): ProcessState =
    ProcessState(
      deploymentId,
      status,
      definitionManager.statusName(status),
      processVersionId,
      definitionManager.statusActions(status),
      definitionManager.statusIcon(status),
      definitionManager.statusTooltip(status),
      definitionManager.statusMessage(status),
      startTime,
      attributes,
      errors
    )

  @JsonCodec case class StateStatusCodec(clazz: String, value: String)
}

@JsonCodec case class ProcessState(deploymentId: DeploymentId,
                                   status: StateStatus,
                                   name: String,
                                   processVersionId: Option[ProcessVersion],
                                   allowedActions: List[ProcessActionType],
                                   icon: Option[URI],
                                   tooltip: Option[String],
                                   message: Option[String],
                                   startTime: Option[Long],
                                   attributes: Option[Json],
                                   errors: Option[String]) {
  def isDeployed: Boolean = status.isRunning || status.isDuringDeploy
}

object ProcessActionType extends Enumeration {
  implicit val typeEncoder: Encoder[ProcessActionType.Value] = Encoder.enumEncoder(ProcessActionType)
  implicit val typeDecoder: Decoder[ProcessActionType.Value] = Decoder.enumDecoder(ProcessActionType)

  type ProcessActionType = Value
  val Deploy: Value = Value("DEPLOY")
  val Cancel: Value = Value("CANCEL")
  val Pause: Value = Value("PAUSE") //TODO: To implement in future..
}

object StateStatus {
  implicit val configuration: Configuration = Configuration
    .default
    .withDefaults
    .withDiscriminator("type")
}

@ConfiguredJsonCodec sealed trait StateStatus {
  def isDuringDeploy: Boolean = false
  def isFinished: Boolean = false
  def isRunning: Boolean = false
  def canDeploy: Boolean = false
  def name: String

  def isFollowingDeployAction: Boolean =
    isDuringDeploy || isRunning || isFinished
}

final case class NotEstablishedStateStatus(val name: String) extends StateStatus

final case class StoppedStateStatus(val name: String) extends StateStatus {
  override def canDeploy: Boolean = true
}

final case class DuringDeployStateStatus(val name: String) extends StateStatus {
  override def isDuringDeploy: Boolean = true
}

final case class FinishedStateStatus(val name: String) extends StateStatus {
  override def isFinished: Boolean = true
  override def canDeploy: Boolean = true
}

final case class RunningStateStatus(val name: String) extends StateStatus {
  override def isRunning: Boolean = true
}
