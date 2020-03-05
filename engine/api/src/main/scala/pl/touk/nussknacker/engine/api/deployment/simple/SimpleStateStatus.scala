package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.{AllowDeployStateStatus, DuringDeployStateStatus, FinishedStateStatus, NotEstablishedStateStatus, RunningStateStatus, StateStatus}

object SimpleStateStatus {
  val FailedToGet: StateStatus = NotEstablishedStateStatus("FAILED_TO_GET")
  val NotFound: StateStatus = AllowDeployStateStatus("NOT_FOUND")
  val Unknown: StateStatus = NotEstablishedStateStatus("UNKNOWN")
  val NotDeployed: StateStatus = AllowDeployStateStatus("NOT_DEPLOYED")
  val DuringDeploy: StateStatus = DuringDeployStateStatus("DURING_DEPLOY")
  val Running: StateStatus = RunningStateStatus("RUNNING")
  val Finished: StateStatus = FinishedStateStatus("FINISHED")
  val DuringCancel: StateStatus = NotEstablishedStateStatus("DURING_CANCEL")
  val Canceled: StateStatus = AllowDeployStateStatus("CANCELED")
  val Failed: StateStatus = AllowDeployStateStatus("FAILED")
  val Error: StateStatus = AllowDeployStateStatus("ERROR")
  val Warning: StateStatus = AllowDeployStateStatus("WARNING")
}
