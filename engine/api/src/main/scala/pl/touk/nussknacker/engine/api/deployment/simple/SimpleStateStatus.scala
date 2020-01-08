package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.{BaseStateStatus, RunningStateStatus, DuringDeployStateStatus, FinishedStateStatus}

object SimpleStateStatus {
  val Unknown = new BaseStateStatus("UNKNOWN")
  val NotDeployed = new BaseStateStatus("NOT_DEPLOYED")
  val DuringDeploy = new DuringDeployStateStatus("DURING_DEPLOY")
  val Running = new RunningStateStatus("RUNNING")
  val Finished = new FinishedStateStatus("FINISHED")
  val Canceled = new BaseStateStatus("CANCELED")
  val Failed = new BaseStateStatus("FAILED")
}
