package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.StateStatus

object FlinkStateStatus {
  import StateStatus._

  val Unknown = new StateStatus("UNKNOWN")
  val NotDeployed = new StateStatus("NOT_DEPLOYED")
  val DuringDeploy = new DuringDeployStateStatus("DURING_DEPLOY")
  val Running = new RunningStateStatus("RUNNING")
  val Finished = new FinishedStateStatus("FINISHED")
  val Restarting = new StateStatus("RESTARTING")
  val DuringCancel = new StateStatus("DURING_CANCEL")
  val Canceled = new StateStatus("CANCELED")
  val Failed = new StateStatus("FAILED")
}
