package pl.touk.nussknacker.restmodel.displayedgraph

import java.net.URI
import io.circe.generic.JsonCodec
import io.circe.Json
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, ProcessStateDefinitionManager, StateStatus}
import pl.touk.nussknacker.restmodel.codecs.URICodecs.{uriDecoder, uriEncoder}

//TODO: Do we really  we need ProcessStatus and ProcessState - Do these DTO's do the same things?
@JsonCodec case class ProcessStatus(status: StateStatus,
                                    deploymentId: Option[String],
                                    allowedActions: List[ProcessActionType],
                                    icon: Option[URI],
                                    tooltip: Option[String],
                                    description: Option[String],
                                    startTime: Option[Long],
                                    attributes: Option[Json],
                                    errors: List[String]) {
}

object ProcessStatus {

  def simple(status: StateStatus): ProcessStatus =
    ProcessStatus(status, SimpleProcessStateDefinitionManager)

  def simple(status: StateStatus, icon: Option[URI], tooltip: Option[String], description: Option[String], previousState: Option[ProcessState]): ProcessStatus =
    ProcessStatus(
      status = status,
      previousState.map(_.deploymentId.value),
      allowedActions = SimpleProcessStateDefinitionManager.statusActions(status),
      icon = if (icon.isDefined) icon else SimpleProcessStateDefinitionManager.statusIcon(status),
      tooltip = if (tooltip.isDefined) tooltip else SimpleProcessStateDefinitionManager.statusTooltip(status),
      description = if (description.isDefined) description else SimpleProcessStateDefinitionManager.statusDescription(status),
      previousState.flatMap(_.startTime),
      previousState.flatMap(_.attributes),
      previousState.map(_.errors).getOrElse(List.empty)
    )

  def apply(status: StateStatus, processStateDefinitionManager: ProcessStateDefinitionManager): ProcessStatus =
    ProcessStatus(status, processStateDefinitionManager, Option.empty, Option.empty, Option.empty, List.empty)

  def apply(status: StateStatus,
            processStateDefinitionManager: ProcessStateDefinitionManager,
            deploymentId: Option[String],
            startTime: Option[Long],
            attributes: Option[Json],
            errors: List[String]): ProcessStatus =
    ProcessStatus(
      status,
      deploymentId,
      allowedActions = processStateDefinitionManager.statusActions(status),
      icon = processStateDefinitionManager.statusIcon(status),
      tooltip = processStateDefinitionManager.statusTooltip(status),
      description = processStateDefinitionManager.statusDescription(status),
      startTime,
      attributes,
      errors
    )

  def apply(processState: ProcessState): ProcessStatus = {
    ProcessStatus(
      deploymentId = Some(processState.deploymentId.value),
      status = processState.status,
      allowedActions = processState.allowedActions,
      icon = processState.icon,
      tooltip = processState.tooltip,
      description = processState.description,
      startTime = processState.startTime,
      attributes = processState.attributes,
      errors = processState.errors
    )
  }

  def simpleErrorShouldBeRunning(deployedVersionId: Long, user: String, previousState: Option[ProcessState]): ProcessStatus = simple(
    status = SimpleStateStatus.Error,
    icon = Some(SimpleProcessStateDefinitionManager.deployFailedIcon),
    tooltip = Some(SimpleProcessStateDefinitionManager.shouldBeRunningTooltip(deployedVersionId, user)),
    description = Some(SimpleProcessStateDefinitionManager.shouldBeRunningDescription),
    previousState = previousState
  )

  def simpleErrorMismatchDeployedVersion(deployedVersionId: Long, exceptedVersionId: Long, user: String, previousState: Option[ProcessState]): ProcessStatus = simple(
    status = SimpleStateStatus.Error,
    icon = Some(SimpleProcessStateDefinitionManager.deployFailedIcon),
    tooltip = Some(SimpleProcessStateDefinitionManager.mismatchDeployedVersionTooltip(deployedVersionId, exceptedVersionId, user)),
    description = Some(SimpleProcessStateDefinitionManager.mismatchDeployedVersionDescription),
    previousState = previousState
  )

  def simpleWarningShouldNotBeRunning(previousState: Option[ProcessState], deployed: Boolean): ProcessStatus = simple(
    status = SimpleStateStatus.Warning,
    icon = Some(SimpleProcessStateDefinitionManager.shouldNotBeRunningIcon(deployed)),
    tooltip = Some(SimpleProcessStateDefinitionManager.shouldNotBeRunningMessage(deployed)),
    description = Some(SimpleProcessStateDefinitionManager.shouldNotBeRunningMessage(deployed)),
    previousState = previousState
  )

  def simpleWarningMissingDeployedVersion(exceptedVersionId: Long, user: String, previousState: Option[ProcessState]): ProcessStatus = simple(
    status = SimpleStateStatus.Warning,
    icon = Some(SimpleProcessStateDefinitionManager.deployWarningIcon),
    tooltip = Some(SimpleProcessStateDefinitionManager.missingDeployedVersionTooltip(exceptedVersionId, user)),
    description = Some(SimpleProcessStateDefinitionManager.missingDeployedVersionDescription),
    previousState = previousState
  )

  def simpleWarningProcessWithoutAction(previousState: Option[ProcessState]): ProcessStatus = simple(
    status = SimpleStateStatus.Warning,
    icon = Some(SimpleProcessStateDefinitionManager.notDeployedWarningIcon),
    tooltip = Some(SimpleProcessStateDefinitionManager.processWithoutActionMessage),
    description = Some(SimpleProcessStateDefinitionManager.processWithoutActionMessage),
    previousState = previousState
  )

  val unknown: ProcessStatus = simple(SimpleStateStatus.Unknown)

  val failedToGet: ProcessStatus = simple(SimpleStateStatus.FailedToGet)

  val notFound: ProcessStatus = simple(SimpleStateStatus.NotFound)
}
