package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.{AllowDeployStateStatus, DuringDeployStateStatus, FailedStateStatus, FinishedStateStatus, NotEstablishedStateStatus, RunningStateStatus, StateStatus}

object SimpleStateStatus {
  val FailedToGet: StateStatus = NotEstablishedStateStatus("FAILED_TO_GET")
  val Unknown: StateStatus = NotEstablishedStateStatus("UNKNOWN")
  val NotDeployed: StateStatus = AllowDeployStateStatus("NOT_DEPLOYED")
  val DuringDeploy: StateStatus = DuringDeployStateStatus("DURING_DEPLOY")
  val Running: StateStatus = RunningStateStatus("RUNNING")
  val Finished: StateStatus = FinishedStateStatus("FINISHED")
  val DuringCancel: StateStatus = NotEstablishedStateStatus("DURING_CANCEL")
  val Canceled: StateStatus = AllowDeployStateStatus("CANCELED")
  val Failed: StateStatus = FailedStateStatus("FAILED")
  val Error: StateStatus = AllowDeployStateStatus("ERROR")
  val Warning: StateStatus = AllowDeployStateStatus("WARNING")
}
