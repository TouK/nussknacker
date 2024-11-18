package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ProcessStatus
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

/**
  * Flink statuses are based on SimpleStateStatus definitions.
  * There are some custom restrictions to allowed actions.
  */
object FlinkStateStatus {

  val statusActionsPF: PartialFunction[ProcessStatus, List[ScenarioActionName]] = {
    case ProcessStatus(SimpleStateStatus.DuringDeploy, _, _) => List(ScenarioActionName.Cancel)
    case ProcessStatus(SimpleStateStatus.Restarting, _, _)   => List(ScenarioActionName.Cancel)
  }

}
