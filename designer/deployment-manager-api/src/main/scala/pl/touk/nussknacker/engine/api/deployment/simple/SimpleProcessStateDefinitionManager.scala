package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName.DefaultActions
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.{ProblemStateStatus, statusActionsPF}
import pl.touk.nussknacker.engine.api.deployment.{
  ProcessStateDefinitionManager,
  ScenarioActionName,
  StateDefinitionDetails,
  StateStatus
}

/**
  * Base [[ProcessStateDefinitionManager]] with basic state definitions and state transitions.
  * Provides methods to handle erroneous edge cases.
  * @see [[SimpleStateStatus]]
  */
object SimpleProcessStateDefinitionManager extends ProcessStateDefinitionManager {

  override def statusActions(input: ScenarioStatusWithScenarioContext): List[ScenarioActionName] =
    statusActionsPF.lift(input.status).getOrElse(DefaultActions)

  override def statusDescription(input: ScenarioStatusWithScenarioContext): String = statusDescription(input.status)

  private[nussknacker] def statusDescription(status: StateStatus): String = status match {
    case _ @ProblemStateStatus(message, _) => message
    case _                                 => SimpleStateStatus.definitions(status.name).description
  }

  override def statusTooltip(input: ScenarioStatusWithScenarioContext): String = statusTooltip(input.status)

  private[nussknacker] def statusTooltip(status: StateStatus): String = status match {
    case _ @ProblemStateStatus(message, _) => message
    case _                                 => SimpleStateStatus.definitions(status.name).tooltip
  }

  override def stateDefinitions: Map[StatusName, StateDefinitionDetails] =
    SimpleStateStatus.definitions

}
