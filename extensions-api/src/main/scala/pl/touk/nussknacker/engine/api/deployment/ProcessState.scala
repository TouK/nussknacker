package pl.touk.nussknacker.engine.api.deployment
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

import java.net.URI

/**
  * Represents status of a scenario.
  * Contains:
  * - status itself and its evaluation moment: status, startTime
  * - how to display in UI: icon, tooltip, description
  * - deployment info: deploymentId, version
  * - which actions are allowed: allowedActions
  * - additional properties: attributes, errors
  *
  * Statuses definition, allowed actions and current scenario ProcessState is defined by [[ProcessStateDefinitionManager]].
  * @param description Short message displayed in top right panel of scenario diagram panel.
  * @param tooltip Message displayed when mouse is hoovering over an icon (both scenarios and diagram panel).
  *                May contain longer, detailed status description.
  */
@JsonCodec case class ProcessState(deploymentId: Option[ExternalDeploymentId],
                                   status: StateStatus,
                                   version: Option[ProcessVersion],
                                   allowedActions: List[ProcessActionType],
                                   icon: URI,
                                   tooltip: String,
                                   description: String,
                                   startTime: Option[Long],
                                   attributes: Option[Json],
                                   errors: List[String]) {

  // TODO: those methods shouldn't be needed after refactors in DeploymentManager: split getProcessState into
  //       fetching only details, and enriching details with presentation properties like icon, tooltip etc.
  def toStatusDetails: StatusDetails = StatusDetails(
    status = status,
    deploymentId = deploymentId,
    version = version,
    startTime = startTime,
    attributes = attributes,
    errors = errors
  )

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

object StateStatus {
  type StatusName = String
  implicit val configuration: Configuration = Configuration
    .default
    .withDefaults
    .withDiscriminator("type")

  // Temporary encoder/decoder
  implicit val statusEncoder: Encoder[StateStatus] = Encoder.encodeString.contramap(_.name)
  implicit val statusDecoder: Decoder[StateStatus] = Decoder.decodeString.map(statusName => new StateStatus(statusName))
}


// Status identifier, should be unique among all states registered within all processing types.
class StateStatus(val name: StatusName) {
  //used for filtering processes (e.g. shouldBeRunning)
  def isDuringDeploy: Boolean = false
  //used for handling finished
  def isFinished: Boolean = false

  def isRunning: Boolean = false
  def isFailed: Boolean = false

  def isDeployed: Boolean = isRunning || isDuringDeploy
}

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
                                  icon: URI,
                                  tooltip: String,
                                  description: String)

object StateDefinitionDetails {
  val UnknownIcon: URI = URI.create("/assets/states/status-unknown.svg")
}

case class StatusDetails(status: StateStatus,
                         deploymentId: Option[ExternalDeploymentId] = None,
                         version: Option[ProcessVersion] = None,
                         startTime: Option[Long] = None,
                         attributes: Option[Json] = None,
                         errors: List[String] = List.empty)