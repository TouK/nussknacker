package pl.touk.nussknacker.restmodel.displayedgraph

import java.net.URI

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}

@JsonCodec(encodeOnly = true) case class ProcessStatus(status: StateStatus,
                                                       deploymentId: Option[String] = Option.empty,
                                                       allowedActions: List[StateAction] = List.empty,
                                                       icon: Option[URI] = Option.empty,
                                                       tooltip: Option[String] = Option.empty,
                                                       startTime: Option[Long] = Option.empty,
                                                       attributes: Option[Json] = Option.empty,
                                                       errorMessage: Option[String] = Option.empty)

object ProcessStatus {

  implicit val typeEncoder: Encoder[StateStatus] = Encoder.encodeString.contramap(_.name)
  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap(_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder.decodeString.map(URI.create)

  def simple(status: StateStatus, deploymentId: Option[String]): ProcessStatus = ProcessStatus(
    status,
    deploymentId,
    allowedActions = SimpleProcessStateDefinitionManager.statusActions(status),
    icon = SimpleProcessStateDefinitionManager.statusIcon(status),
    tooltip = SimpleProcessStateDefinitionManager.statusTooltip(status)
  )

  def create(processState: ProcessState, expectedDeploymentVersion: Option[Long]): ProcessStatus = {
    val versionMatchMessage = (processState.version, expectedDeploymentVersion) match {
      //currently returning version is optional
      case (None, _) => None
      case (Some(stateVersion), Some(expectedVersion)) if stateVersion.versionId == expectedVersion => None
      case (Some(stateVersion), Some(expectedVersion)) => Some(s"Process deployed in version ${stateVersion.versionId} (by ${stateVersion.user}), expected version $expectedVersion")
      case (Some(stateVersion), None) => Some(s"Process deployed in version ${stateVersion.versionId} (by ${stateVersion.user}), should not be deployed")
    }

    ProcessStatus(
      deploymentId = Some(processState.deploymentId.value),
      status = processState.status,
      allowedActions = processState.allowedActions,
      icon = processState.icon,
      tooltip = processState.tooltip,
      startTime = processState.startTime,
      attributes = processState.attributes,
      errorMessage = List(versionMatchMessage, processState.errorMessage).flatten.reduceOption(_  + ", " + _)
    )
  }

  val failedToGet: ProcessStatus = simple(SimpleStateStatus.FailedToGet, Option.empty)

  val notFound: ProcessStatus = simple(SimpleStateStatus.NotFound, Option.empty)
}
