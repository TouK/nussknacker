package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.StateStatus

object SimpleStateStatus {
  import StateStatus._

  val Unknown = new StateStatus("UNKNOWN")
  val NotDeployed = new StateStatus("NOT_DEPLOYED")
  val DuringDeploy = new DuringDeployStateStatus("DURING_DEPLOY")
  val Running = new RunningStateStatus("RUNNING")
  val Finished = new FinishedStateStatus("FINISHED")
  val Canceled = new StateStatus("CANCELED")
  val Failed = new StateStatus("FAILED")
}
