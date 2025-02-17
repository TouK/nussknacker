package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

/**
  * Flink statuses are based on SimpleStateStatus definitions.
  * There are some custom restrictions to allowed actions.
  */
object FlinkStateStatus {

  val statusActionsPF: PartialFunction[ScenarioStatusWithScenarioContext, List[ScenarioActionName]] = {
    case input if input.status == SimpleStateStatus.DuringDeploy => List(ScenarioActionName.Cancel)
    case input if input.status == SimpleStateStatus.Restarting   => List(ScenarioActionName.Cancel)
  }

}
