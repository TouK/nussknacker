package pl.touk.nussknacker.engine.api.deployment

import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StateId
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

import java.net.URI

//@TODO: In future clean up it.
trait ProcessStateDefinitionManager {

  //State properties that describe how state is displayed in UI for each scenario
  //Here you can handle dynamic properties of states such as schedule date in PeriodicProcessStateDefinitionManager
  //...But why do we have schedule date or progress bar "inside" status?
  //Shouldn't it be a series of transitions between states?
  //Or somehow separate constant part of state (as state model) from dynamic visualization
  def statusActions(stateStatus: StateStatus): List[ProcessActionType]
  def statusTooltip(stateStatus: StateStatus): Option[String]
  def statusDescription(stateStatus: StateStatus): Option[String]
  def statusIcon(stateStatus: StateStatus): Option[URI]
  //Temporary mapping ProcessActionType to StateStatus. TODO: Remove it when we will support state cache
  def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus

  //Static parts of state definitions for filtering by status ID (aka name)
  //Here dynamic aspects of states (transitions or properties) are ignored
  //...Again, why do we have those dynamic properties?
  def statusIds(): Set[StateId]
  def statusDisplayableName(name: StateId): String
  def statusIcon(name: StateId): Option[URI]

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