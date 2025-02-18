package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

/**
  * Flink statuses are based on SimpleStateStatus definitions.
  * There are some custom restrictions to allowed actions.
  */
object FlinkStateStatus {

  val statusActionsPF: PartialFunction[ScenarioStatusWithScenarioContext, Set[ScenarioActionName]] = {
    case input if input.scenarioStatus == SimpleStateStatus.DuringDeploy => Set(ScenarioActionName.Cancel)
    case input if input.scenarioStatus == SimpleStateStatus.Restarting   => Set(ScenarioActionName.Cancel)
  }

}
