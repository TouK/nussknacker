package pl.touk.nussknacker.engine.defaults.deployment

import pl.touk.nussknacker.engine.api.deployment.StatusState

object DefaultStateStatus extends Enumeration {
  import StatusState._

  val Running = new StateStatus("RUNNING")
  val Finished = new StateStatus("FINISHED")
  val DuringDeploy = new StateStatus("DURING_DEPLOY")
  val Unknown = new StateStatus("UNKNOWN")
  val NotDeployed = new StateStatus("NOT_DEPLOYED")
  val Restarting = new StateStatus("RESTARTING")
  val Failed = new StateStatus("FAILED")
  val DuringCancel = new StateStatus("DURING_CANCEL")
  val Canceled = new StateStatus("CANCELED")
}
