package pl.touk.nussknacker.engine.api.deployment

import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

import java.net.URI

//@TODO: In future clean up it.
trait ProcessStateDefinitionManager {

  //State properties that describe how state is displayed in UI for each scenario
  //Here you can handle dynamic properties of states such as schedule date in PeriodicProcessStateDefinitionManager
  def statusActions(stateStatus: StateStatus): List[ProcessActionType]
  def statusTooltip(stateStatus: StateStatus): Option[String]
  def statusDescription(stateStatus: StateStatus): Option[String]
  def statusIcon(stateStatus: StateStatus): Option[URI]
  //Temporary mapping ProcessActionType to StateStatus. TODO: Remove it when we will support state cache
  def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus

  //Static parts of state definitions for filtering by status ID (aka name)
  //Here dynamic aspects of states (transitions or properties) are ignored
  def stateNames(): Set[StatusName]
  def stateDisplayableName(name: StatusName): String
  def stateIcon(name: StatusName): Option[URI]

  def stateDefinitions(): Set[StateDefinition] =
    stateNames().map(name => StateDefinition(name, stateDisplayableName(name), stateIcon(name)))

  def processState(status: StateStatus,
                   deploymentId: Option[ExternalDeploymentId] = None,
                   version: Option[ProcessVersion] = None,
                   startTime: Option[Long] = None,
                   attributes: Option[Json] = None,
                   errors: List[String] = List.empty): ProcessState = {
    ProcessState(
      deploymentId,
      status,
      version,
      statusActions(status),
      statusIcon(status),
      statusTooltip(status),
      statusDescription(status),
      startTime,
      attributes,
      errors)
  }

}