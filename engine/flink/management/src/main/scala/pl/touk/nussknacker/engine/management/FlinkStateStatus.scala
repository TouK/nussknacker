package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.NotEstablishedStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

object FlinkStateStatus  {
  val FailedToGet = SimpleStateStatus.FailedToGet
  val NotFound = SimpleStateStatus.NotFound
  val Unknown = SimpleStateStatus.Unknown
  val NotDeployed = SimpleStateStatus.NotDeployed
  val DuringDeploy = SimpleStateStatus.DuringDeploy
  val Running = SimpleStateStatus.Running
  val Finished = SimpleStateStatus.Finished
  val Restarting = new NotEstablishedStateStatus("RESTARTING")
  val DuringCancel = SimpleStateStatus.DuringCancel
  val Canceled = SimpleStateStatus.Canceled
  val Failed = SimpleStateStatus.Failed
  val Error = SimpleStateStatus.Error
  val Warning = SimpleStateStatus.Warning
}
