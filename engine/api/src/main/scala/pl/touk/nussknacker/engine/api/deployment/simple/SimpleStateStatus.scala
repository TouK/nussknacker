package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.{DuringDeployStateStatus, ErrorStateStatus, FinishedStateStatus, NotEstablishedStateStatus, RunningStateStatus, StoppedStateStatus}

object SimpleStateStatus {
  val FailedToGet: NotEstablishedStateStatus = NotEstablishedStateStatus("FAILED_TO_GET")
  val NotFound: StoppedStateStatus = StoppedStateStatus("NOT_FOUND")
  val Unknown: NotEstablishedStateStatus = NotEstablishedStateStatus("UNKNOWN")
  val NotDeployed: StoppedStateStatus = StoppedStateStatus("NOT_DEPLOYED")
  val DuringDeploy: DuringDeployStateStatus = DuringDeployStateStatus("DURING_DEPLOY")
  val Running: RunningStateStatus = RunningStateStatus("RUNNING")
  val Finished: FinishedStateStatus = FinishedStateStatus("FINISHED")
  val DuringCancel: NotEstablishedStateStatus = NotEstablishedStateStatus("DURING_CANCEL")
  val Canceled: StoppedStateStatus = StoppedStateStatus("CANCELED")
  val Failed: ErrorStateStatus = ErrorStateStatus("FAILED")
  val Error: ErrorStateStatus = ErrorStateStatus("ERROR")
  val RunningError: ErrorStateStatus = ErrorStateStatus("RUNNING_ERROR")
}
