package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ProcessStatus
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

/**
  * Flink statuses are based on SimpleStateStatus definitions.
  * There are some custom restrictions to allowed actions.
  */
object FlinkStateStatus {

  val statusActionsPF: PartialFunction[ProcessStatus, List[ScenarioActionName]] = _.stateStatus match {
    case SimpleStateStatus.DuringDeploy => List(ScenarioActionName.Cancel)
    case SimpleStateStatus.Restarting   => List(ScenarioActionName.Cancel)
  }

}
