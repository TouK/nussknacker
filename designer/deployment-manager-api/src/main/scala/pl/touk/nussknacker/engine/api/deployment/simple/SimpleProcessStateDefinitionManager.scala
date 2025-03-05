package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.{
  ProcessStateDefinitionManager,
  ScenarioActionName,
  StateDefinitionDetails,
  StateStatus
}
import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName.DefaultActions
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.{statusActionsPF, ProblemStateStatus}

/**
  * Base [[ProcessStateDefinitionManager]] with basic state definitions and state transitions.
  * Provides methods to handle erroneous edge cases.
  * @see [[SimpleStateStatus]]
  */
object SimpleProcessStateDefinitionManager extends ProcessStateDefinitionManager {

  override def statusActions(input: ScenarioStatusWithScenarioContext): Set[ScenarioActionName] =
    statusActionsPF.lift(input.scenarioStatus).getOrElse(DefaultActions.toSet)

  override def statusDescription(input: ScenarioStatusWithScenarioContext): String = statusDescription(
    input.scenarioStatus
  )

  private[nussknacker] def statusDescription(status: StateStatus): String = status match {
    case _ @ProblemStateStatus(message, _, _) => message
    case _                                    => SimpleStateStatus.definitions(status.name).description
  }

  override def statusTooltip(input: ScenarioStatusWithScenarioContext): String = statusTooltip(input.scenarioStatus)

  private[nussknacker] def statusTooltip(status: StateStatus): String = status match {
    case _ @ProblemStateStatus(message, _, Some(tooltip)) => tooltip
    case _ @ProblemStateStatus(message, _, _)             => message
    case _                                                => SimpleStateStatus.definitions(status.name).tooltip
  }

  override def stateDefinitions: Map[StatusName, StateDefinitionDetails] =
    SimpleStateStatus.definitions

}
