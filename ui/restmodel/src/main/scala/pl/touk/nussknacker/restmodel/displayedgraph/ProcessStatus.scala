package pl.touk.nussknacker.restmodel.displayedgraph

import java.net.URI

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, ProcessStateDefinitionManager, StateStatus}

//TODO: Do we really  we need ProcessStatus and ProcessState - Do these DTO's do the same things?
@JsonCodec case class ProcessStatus(status: StateStatus,
                                    deploymentId: Option[String],
                                    allowedActions: List[ProcessActionType],
                                    icon: Option[URI],
                                    tooltip: Option[String],
                                    description: Option[String],
                                    startTime: Option[Long],
                                    attributes: Option[Json],
                                    errors: List[String])

object ProcessStatus {
  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap(_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder.decodeString.map(URI.create)

  def simple(status: StateStatus, deploymentId: Option[String], errors: List[String]): ProcessStatus =
    ProcessStatus(status, SimpleProcessStateDefinitionManager, deploymentId, Option.empty, Option.empty, errors)

  def simple(status: StateStatus): ProcessStatus =
    ProcessStatus(status, SimpleProcessStateDefinitionManager)

  def simpleError(errorStatus: StateStatus, tooltip: Option[String], description: Option[String], previousState: Option[ProcessState]): ProcessStatus =
    ProcessStatus(
      status = errorStatus,
      previousState.map(_.deploymentId.value),
      allowedActions = SimpleProcessStateDefinitionManager.statusActions(errorStatus),
      icon = SimpleProcessStateDefinitionManager.statusIcon(errorStatus),
      tooltip = if (tooltip.isDefined) tooltip else SimpleProcessStateDefinitionManager.statusTooltip(errorStatus),
      description = if (description.isDefined) description else SimpleProcessStateDefinitionManager.statusDescription(errorStatus),
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

  def simpleErrorShouldRunning(deployedVersionId: Long, user: String, previousState: Option[ProcessState]): ProcessStatus = simpleError(
    errorStatus = SimpleStateStatus.RunningError,
    tooltip = Some(SimpleProcessStateDefinitionManager.errorShouldRunningTooltip(deployedVersionId, user)),
    description = Some(SimpleProcessStateDefinitionManager.errorShouldRunningDescription),
    previousState = previousState
  )

  def simpleErrorShouldNotBeRunning(deployedVersionId: Long, user: String, previousState: Option[ProcessState]): ProcessStatus = simpleError(
    errorStatus = SimpleStateStatus.RunningError,
    tooltip = Some(SimpleProcessStateDefinitionManager.errorShouldNotBeRunningTooltip(deployedVersionId, user)),
    description = Some(SimpleProcessStateDefinitionManager.errorShouldNotBeRunningDescription),
    previousState = previousState
  )

  def simpleErrorShouldNotBeRunning(previousState: Option[ProcessState]): ProcessStatus = simpleError(
    errorStatus = SimpleStateStatus.RunningError,
    tooltip = Some(SimpleProcessStateDefinitionManager.errorShouldNotBeRunningDescription),
    description = Some(SimpleProcessStateDefinitionManager.errorShouldNotBeRunningDescription),
    previousState = previousState
  )

  def simpleErrorMismatchDeployedVersion(deployedVersionId: Long, exceptedVersionId: Long, user: String, previousState: Option[ProcessState]): ProcessStatus = simpleError(
    errorStatus = SimpleStateStatus.RunningError,
    tooltip = Some(SimpleProcessStateDefinitionManager.errorMismatchDeployedVersionTooltip(deployedVersionId, exceptedVersionId, user)),
    description = Some(SimpleProcessStateDefinitionManager.errorMismatchDeployedVersionDescription),
    previousState = previousState
  )

  def simpleErrorMissingDeployedVersion(exceptedVersionId: Long, user: String, previousState: Option[ProcessState]): ProcessStatus = simpleError(
    errorStatus = SimpleStateStatus.RunningError,
    tooltip = Some(SimpleProcessStateDefinitionManager.errorMissingDeployedVersionTooltip(exceptedVersionId, user)),
    description = Some(SimpleProcessStateDefinitionManager.errorMissingDeployedVersionDescription),
    previousState = previousState
  )

  def simpleErrorProcessWithoutAction(previousState: Option[ProcessState]): ProcessStatus = simpleError(
    errorStatus = SimpleStateStatus.RunningError,
    tooltip = Some(SimpleProcessStateDefinitionManager.errorProcessWithoutAction),
    description = Some(SimpleProcessStateDefinitionManager.errorProcessWithoutAction),
    previousState = previousState
  )

  val unknown: ProcessStatus = simple(SimpleStateStatus.Unknown)

  val failedToGet: ProcessStatus = simple(SimpleStateStatus.FailedToGet)

  val notFound: ProcessStatus = simple(SimpleStateStatus.NotFound)
}
