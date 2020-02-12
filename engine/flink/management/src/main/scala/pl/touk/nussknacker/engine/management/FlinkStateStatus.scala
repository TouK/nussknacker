package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.{DuringDeployStateStatus, ErrorStateStatus, FinishedStateStatus, NotEstablishedStateStatus, RunningStateStatus, StateStatus, StoppedStateStatus}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

object FlinkStateStatus  {
  val FailedToGet: StateStatus = SimpleStateStatus.FailedToGet
  val NotFound: StateStatus = SimpleStateStatus.NotFound
  val Unknown: StateStatus = SimpleStateStatus.Unknown
  val NotDeployed: StateStatus = SimpleStateStatus.NotDeployed
  val DuringDeploy: StateStatus = SimpleStateStatus.DuringDeploy
  val Running: StateStatus = SimpleStateStatus.Running
  val Finished: StateStatus = SimpleStateStatus.Finished
  val Restarting: StateStatus = NotEstablishedStateStatus("RESTARTING")
  val DuringCancel: StateStatus = SimpleStateStatus.DuringCancel
  val Canceled: StateStatus = SimpleStateStatus.Canceled
  val Failed: StateStatus = SimpleStateStatus.Failed
  val Error: StateStatus = SimpleStateStatus.Error
}
