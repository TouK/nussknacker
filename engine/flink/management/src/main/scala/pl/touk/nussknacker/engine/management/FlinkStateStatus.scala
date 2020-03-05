package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment._

object FlinkStateStatus  {
  val FailedToGet: NotEstablishedStateStatus = SimpleStateStatus.FailedToGet
  val NotFound: AllowDeployStateStatus = SimpleStateStatus.NotFound
  val Unknown: NotEstablishedStateStatus = SimpleStateStatus.Unknown
  val NotDeployed: AllowDeployStateStatus = SimpleStateStatus.NotDeployed
  val DuringDeploy: DuringDeployStateStatus = SimpleStateStatus.DuringDeploy
  val Running: RunningStateStatus = SimpleStateStatus.Running
  val Finished: FinishedStateStatus = SimpleStateStatus.Finished
  val Restarting: NotEstablishedStateStatus = NotEstablishedStateStatus("RESTARTING")
  val DuringCancel: NotEstablishedStateStatus = SimpleStateStatus.DuringCancel
  val Canceled: AllowDeployStateStatus = SimpleStateStatus.Canceled
  val Failed: AllowDeployStateStatus = SimpleStateStatus.Failed
  val Error: AllowDeployStateStatus = SimpleStateStatus.Error
  val Warning: AllowDeployStateStatus = SimpleStateStatus.Warning
}
