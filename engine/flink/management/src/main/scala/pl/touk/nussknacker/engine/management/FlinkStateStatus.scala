package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

object FlinkStateStatus {

  // Flink statuses are based on SimpleStateStatus definitions
  // TODO: remove those states here and use SimpleStateStatus
  val NotDeployed: StateStatus = SimpleStateStatus.NotDeployed
  val DuringDeploy: StateStatus = SimpleStateStatus.DuringDeploy
  val Running: StateStatus = SimpleStateStatus.Running
  val Finished: StateStatus = SimpleStateStatus.Finished
  val Restarting: StateStatus = SimpleStateStatus.Restarting
  val DuringCancel: StateStatus = SimpleStateStatus.DuringCancel
  val Canceled: StateStatus = SimpleStateStatus.Canceled

  val statusActionsPF: PartialFunction[StateStatus, List[ProcessActionType]] = {
    case FlinkStateStatus.DuringDeploy => List(ProcessActionType.Cancel)
    case FlinkStateStatus.Restarting => List(ProcessActionType.Cancel)
  }

}
