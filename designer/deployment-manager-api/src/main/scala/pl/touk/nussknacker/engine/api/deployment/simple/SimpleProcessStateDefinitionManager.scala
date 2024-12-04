package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ProcessStatus
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName.DefaultActions
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.{ProblemStateStatus, statusActionsPF}
import pl.touk.nussknacker.engine.api.deployment.{
  ProcessState,
  ProcessStateDefinitionManager,
  ScenarioActionName,
  StateDefinitionDetails,
  StateStatus,
  StatusDetails
}
import pl.touk.nussknacker.engine.api.process.VersionId

/**
  * Base [[ProcessStateDefinitionManager]] with basic state definitions and state transitions.
  * Provides methods to handle erroneous edge cases.
  * @see [[SimpleStateStatus]]
  */
object SimpleProcessStateDefinitionManager extends ProcessStateDefinitionManager {

  override def statusActions(processStatus: ProcessStatus): List[ScenarioActionName] =
    statusActionsPF.applyOrElse(processStatus, (_: ProcessStatus) => DefaultActions)

  override def statusDescription(stateStatus: StateStatus): String = stateStatus match {
    case _ @ProblemStateStatus(message, _) => message
    case _                                 => SimpleStateStatus.definitions(stateStatus.name).description
  }

  override def statusTooltip(stateStatus: StateStatus): String = stateStatus match {
    case _ @ProblemStateStatus(message, _) => message
    case _                                 => SimpleStateStatus.definitions(stateStatus.name).tooltip
  }

  override def stateDefinitions: Map[StatusName, StateDefinitionDetails] =
    SimpleStateStatus.definitions

  def errorFailedToGet(versionId: VersionId): ProcessState =
    processState(StatusDetails(FailedToGet, None), versionId, None)

}
