package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.{DuringDeployStateStatus, AllowDeployStateStatus, FinishedStateStatus, NotEstablishedStateStatus, RunningStateStatus}

object SimpleStateStatus {
  val FailedToGet: NotEstablishedStateStatus = NotEstablishedStateStatus("FAILED_TO_GET")
  val NotFound: AllowDeployStateStatus = AllowDeployStateStatus("NOT_FOUND")
  val Unknown: NotEstablishedStateStatus = NotEstablishedStateStatus("UNKNOWN")
  val NotDeployed: AllowDeployStateStatus = AllowDeployStateStatus("NOT_DEPLOYED")
  val DuringDeploy: DuringDeployStateStatus = DuringDeployStateStatus("DURING_DEPLOY")
  val Running: RunningStateStatus = new RunningStateStatus("RUNNING")
  val Finished: FinishedStateStatus = new FinishedStateStatus("FINISHED")
  val DuringCancel: NotEstablishedStateStatus = NotEstablishedStateStatus("DURING_CANCEL")
  val Canceled: AllowDeployStateStatus = AllowDeployStateStatus("CANCELED")
  val Failed: AllowDeployStateStatus = AllowDeployStateStatus("FAILED")
  val Error: AllowDeployStateStatus = AllowDeployStateStatus("ERROR")
  val Warning: AllowDeployStateStatus = AllowDeployStateStatus("WARNING")
}
