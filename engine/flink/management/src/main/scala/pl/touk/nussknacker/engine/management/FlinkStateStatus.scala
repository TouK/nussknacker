package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.{DuringDeployStateStatus, FinishedStateStatus, NotEstablishedStateStatus, RunningStateStatus, StoppedStateStatus}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

object FlinkStateStatus  {
  val FailedToGet: NotEstablishedStateStatus = SimpleStateStatus.FailedToGet
  val NotFound: NotEstablishedStateStatus = SimpleStateStatus.NotFound
  val Unknown: NotEstablishedStateStatus = SimpleStateStatus.Unknown
  val NotDeployed: NotEstablishedStateStatus = SimpleStateStatus.NotDeployed
  val DuringDeploy: DuringDeployStateStatus = SimpleStateStatus.DuringDeploy
  val Running: RunningStateStatus = SimpleStateStatus.Running
  val Finished: FinishedStateStatus = SimpleStateStatus.Finished
  val Restarting: NotEstablishedStateStatus = NotEstablishedStateStatus("RESTARTING")
  val DuringCancel: NotEstablishedStateStatus = SimpleStateStatus.DuringCancel
  val Canceled: StoppedStateStatus = SimpleStateStatus.Canceled
  val Failed: NotEstablishedStateStatus = SimpleStateStatus.Failed
  val Error: NotEstablishedStateStatus = SimpleStateStatus.Error
  val Warning: NotEstablishedStateStatus = SimpleStateStatus.Warning
}
