package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

/**
  * Flink statuses are based on SimpleStateStatus definitions.
  * There are some custom restrictions to allowed actions.
  */
object FlinkStateStatus {

  val statusActionsPF: PartialFunction[StateStatus, List[ProcessActionType]] = {
    case SimpleStateStatus.DuringDeploy => List(ProcessActionType.Cancel)
    case SimpleStateStatus.Restarting   => List(ProcessActionType.Cancel)
  }

}
