package pl.touk.nussknacker.engine.api.deployment
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

import java.net.URI

@JsonCodec case class ProcessState(deploymentId: Option[ExternalDeploymentId],
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

  // TODO: split status details and scenario deployment details (deploymentId, version, attributes)
  def withStatusDetails(stateWithStatusDetails: ProcessState): ProcessState = {
    copy(
      status = stateWithStatusDetails.status,
      allowedActions = stateWithStatusDetails.allowedActions,
      icon = stateWithStatusDetails.icon,
      tooltip = stateWithStatusDetails.tooltip,
      description = stateWithStatusDetails.description)
  }

}

object ProcessState {
  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap(_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder.decodeString.map(URI.create)
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
}

object ProcessActionState extends Enumeration {
  implicit val typeEncoder: Encoder[ProcessActionState.Value] = Encoder.encodeEnumeration(ProcessActionState)
  implicit val typeDecoder: Decoder[ProcessActionState.Value] = Decoder.decodeEnumeration(ProcessActionState)

  type ProcessActionState = Value
  val InProgress: Value = Value("IN_PROGRESS")
  val Finished: Value = Value("FINISHED")
  val Failed: Value = Value("FAILED")
}

object StateStatus {
  type StatusName = String
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
  def name: StatusName

}

final case class AllowDeployStateStatus(name: StatusName) extends StateStatus

final case class NotEstablishedStateStatus(name: StatusName) extends StateStatus

final case class DuringDeployStateStatus(name: StatusName) extends StateStatus {
  override def isDuringDeploy: Boolean = true
}

final case class FinishedStateStatus(name: StatusName) extends StateStatus {
  override def isFinished: Boolean = true
}

final case class RunningStateStatus(name: StatusName) extends StateStatus {
  override def isRunning: Boolean = true
}

final case class FailedStateStatus(name: StatusName) extends StateStatus {
  override def isFailed: Boolean = true
}

// This status class is a walk around for fact that StateStatus is encoded and decoded. It causes that there is no easy option
// to add own status with some specific fields without passing Encoders and Decoders to many places in application.
// TODO: we should find places where StateStatuses are encoded and decoded and replace them with some DTOs for this purpose
class CustomStateStatus(val name: StatusName) extends StateStatus

/**
  * It is used to specify:
  * <ul>
  *   <li>fixed default properties of a status: icon, tooltip, descripition
  *   <li>fixed set of properties of filtering options: displayableName, icon tooltip
  * </ul>
  * When a status has dynamic properties use ProcessStateDefinitionManager to handle them.
  *
  * @see default values of a status in [[ProcessStateDefinitionManager]]
  * @see filtering options in [[UIStateDefinition]]
  * @see overriding state definitions in [[OverridingProcessStateDefinitionManager]]
  */
case class StateDefinitionDetails(displayableName: String,
                                  icon: Option[URI],
                                  tooltip: Option[String],
                                  description: Option[String])
