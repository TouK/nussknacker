package pl.touk.nussknacker.restmodel.displayedgraph

import java.net.URI

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, ProcessStateDefinitionManager, StateStatus}
import pl.touk.nussknacker.restmodel.processdetails.ProcessDeploymentAction

//TODO: Do we really  we need ProcessStatus and ProcessState - Do these DTO's do the same things?
@JsonCodec case class ProcessStatus(status: StateStatus,
                                    name: String,
                                    deploymentId: Option[String],
                                    allowedActions: List[ProcessActionType],
                                    icon: Option[URI],
                                    tooltip: Option[String],
                                    message: Option[String],
                                    startTime: Option[Long],
                                    attributes: Option[Json],
                                    errors: Option[String])

object ProcessStatus {
  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap(_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder.decodeString.map(URI.create)

  def simple(status: StateStatus, deploymentId: Option[String], errors: Option[String]): ProcessStatus =
    ProcessStatus(status, SimpleProcessStateDefinitionManager, deploymentId, Option.empty, Option.empty, errors)

  def simple(status: StateStatus): ProcessStatus =
    ProcessStatus(status, SimpleProcessStateDefinitionManager)

  def apply(status: StateStatus, processStateDefinitionManager: ProcessStateDefinitionManager): ProcessStatus =
    ProcessStatus(status, processStateDefinitionManager, Option.empty, Option.empty, Option.empty, Option.empty)

  def apply(status: StateStatus,
            processStateDefinitionManager: ProcessStateDefinitionManager,
            deploymentId: Option[String],
            startTime: Option[Long],
            attributes: Option[Json],
            errors: Option[String]): ProcessStatus =
    ProcessStatus(
      status,
      processStateDefinitionManager.statusName(status),
      deploymentId,
      allowedActions = processStateDefinitionManager.statusActions(status),
      icon = processStateDefinitionManager.statusIcon(status),
      tooltip = processStateDefinitionManager.statusTooltip(status),
      message = processStateDefinitionManager.statusMessage(status),
      startTime,
      attributes,
      errors
    )

  def create(processState: ProcessState, lastAction: Option[ProcessDeploymentAction]): ProcessStatus = {
    val mismatchMessage = deployedVersionMismatchMessage(processState, lastAction)

    ProcessStatus(
      deploymentId = Some(processState.deploymentId.value),
      status = processState.status,
      name = processState.name,
      allowedActions = processState.allowedActions,
      icon = processState.icon,
      tooltip = processState.tooltip,
      message = processState.message,
      startTime = processState.startTime,
      attributes = processState.attributes,
      errors = List(mismatchMessage, processState.errors).flatten.reduceOption(_ + ", " + _)
    )
  }

  //TODO: Move this logic to another place.. This should be moved together with ManagementActor.handleObsoleteStatus
  private def deployedVersionMismatchMessage(processState: ProcessState, lastAction: Option[ProcessDeploymentAction]) = {
    (processState.version, lastAction) match {
      case (Some(stateVersion), Some(action)) if stateVersion.versionId == action.processVersionId => None
      case (Some(stateVersion), Some(action)) if action.isDeployed && !processState.status.isFollowingDeployAction => Some(s"Process deployed in version ${stateVersion.versionId} (by ${stateVersion.user}), but currently not working")
      case (Some(stateVersion), Some(action)) if action.isDeployed && stateVersion.versionId != action.processVersionId => Some(s"Process deployed in version ${stateVersion.versionId} (by ${stateVersion.user}), expected version ${action.processVersionId}")
      case (Some(stateVersion), None) if processState.isDeployed => Some(s"Process deployed in version ${stateVersion.versionId} (by ${stateVersion.user}), should not be deployed")
      case (None, None) => None
      case _ => None //We verify only deployed process
    }
  }

  def canceled(processStateDefinitionManager: ProcessStateDefinitionManager): ProcessStatus =
    ProcessStatus(SimpleStateStatus.Canceled, processStateDefinitionManager)

  val unknown: ProcessStatus = simple(SimpleStateStatus.Unknown)

  val failedToGet: ProcessStatus = simple(SimpleStateStatus.FailedToGet)

  val notFound: ProcessStatus = simple(SimpleStateStatus.NotFound)
}
