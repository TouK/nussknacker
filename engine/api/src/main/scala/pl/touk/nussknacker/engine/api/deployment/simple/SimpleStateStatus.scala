package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.{DuringDeployStateStatus, FinishedStateStatus, NotEstablishedStateStatus, RunningStateStatus, StoppedStateStatus}

object SimpleStateStatus {
  val FailedToGet = new NotEstablishedStateStatus("FAILED_TO_GET")
  val NotFound = new NotEstablishedStateStatus("NOT_FOUND")
  val Unknown = new NotEstablishedStateStatus("UNKNOWN")
  val NotDeployed = new NotEstablishedStateStatus("NOT_DEPLOYED")
  val DuringDeploy = new DuringDeployStateStatus("DURING_DEPLOY")
  val Running = new RunningStateStatus("RUNNING")
  val Finished = new FinishedStateStatus("FINISHED")
  val DuringCancel = new NotEstablishedStateStatus("DURING_CANCEL")
  val Canceled = new StoppedStateStatus("CANCELED")
  val Failed = new NotEstablishedStateStatus("FAILED")
  val Error = new NotEstablishedStateStatus("ERROR")
  val Warning = new NotEstablishedStateStatus("WARNING")
}
