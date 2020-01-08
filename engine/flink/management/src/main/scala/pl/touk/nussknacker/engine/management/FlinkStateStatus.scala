package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.{BaseStateStatus, RunningStateStatus, DuringDeployStateStatus, FinishedStateStatus}

object FlinkStateStatus {
  val Unknown = new BaseStateStatus("UNKNOWN")
  val NotDeployed = new BaseStateStatus("NOT_DEPLOYED")
  val DuringDeploy = new DuringDeployStateStatus("DURING_DEPLOY")
  val Running = new RunningStateStatus("RUNNING")
  val Finished = new FinishedStateStatus("FINISHED")
  val Restarting = new BaseStateStatus("RESTARTING")
  val DuringCancel = new BaseStateStatus("DURING_CANCEL")
  val Canceled = new BaseStateStatus("CANCELED")
  val Failed = new BaseStateStatus("FAILED")
}
