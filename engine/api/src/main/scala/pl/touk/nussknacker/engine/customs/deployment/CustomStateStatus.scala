package pl.touk.nussknacker.engine.customs.deployment

import pl.touk.nussknacker.engine.api.deployment.StatusState

object CustomStateStatus extends Enumeration {
  import StatusState._

  val Running = StatusState.Running
  val Finished = StatusState.Finished
  val DuringDeploy = StatusState.DuringDeploy
  val Unknown = new StateStatus("UNKNOWN")
  val NotDeployed = new StateStatus("NOT_DEPLOYED")
  val Restarting = new StateStatus("RESTARTING")
  val Failed = new StateStatus("FAILED")
  val DuringCancel = new StateStatus("DURING_CANCEL")
  val Canceled = new StateStatus("CANCELED")
}
