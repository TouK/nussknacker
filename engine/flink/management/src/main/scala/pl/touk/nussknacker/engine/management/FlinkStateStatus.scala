package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, StateStatus}
import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

/**
  * Flink statuses are based on SimpleStateStatus definitions.
  * There are some custom restrictions to allowed actions.
  */
object FlinkStateStatus {

  val statusActionsPF: PartialFunction[ScenarioStatusWithScenarioContext, Set[ScenarioActionName]] = {
    case input if isCancelable(input.scenarioStatus) => Set(ScenarioActionName.Cancel)
  }

  private def isCancelable(scenarioStatus: StateStatus) = scenarioStatus match {
    case _: SimpleStateStatus.DuringDeploy   => true
    case _: SimpleStateStatus.DuringRedeploy => true
    case SimpleStateStatus.Restarting        => true
    case _                                   => false
  }

}
