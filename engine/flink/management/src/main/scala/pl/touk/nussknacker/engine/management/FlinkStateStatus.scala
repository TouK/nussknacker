package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

import java.net.URI

object FlinkStateStatus {
  val FailedToGet: StateStatus = SimpleStateStatus.FailedToGet
  val Unknown: StateStatus = SimpleStateStatus.Unknown
  val NotDeployed: StateStatus = SimpleStateStatus.NotDeployed
  val DuringDeploy: StateStatus = SimpleStateStatus.DuringDeploy
  val Running: StateStatus = SimpleStateStatus.Running
  val Finished: StateStatus = SimpleStateStatus.Finished
  val Restarting: StateStatus = SimpleStateStatus.Restarting
  val DuringCancel: StateStatus = SimpleStateStatus.DuringCancel
  val Canceled: StateStatus = SimpleStateStatus.Canceled
  val Failed: StateStatus = SimpleStateStatus.Failed
  val Error: StateStatus = SimpleStateStatus.Error
  val Warning: StateStatus = SimpleStateStatus.Warning

  val Failing: StateStatus =  NotEstablishedStateStatus("FAILING")
  val MultipleJobsRunning: StateStatus = NotEstablishedStateStatus("MULTIPLE_JOBS_RUNNING")

  val statusActionsPF: PartialFunction[StateStatus, List[ProcessActionType]] = {
    case SimpleStateStatus.DuringDeploy => List(ProcessActionType.Cancel)
    case SimpleStateStatus.Restarting => List(ProcessActionType.Cancel)
    case FlinkStateStatus.MultipleJobsRunning => List(ProcessActionType.Cancel)
  }

  val customStateDefinitions: Set[StateDefinition] = Set(
    StateDefinition(
      name = Failing.name,
      displayableName = "Failing",
      icon = Some(URI.create("/assets/states/error.svg")),
      tooltip = Some("Failing"),
      description = Some("Failing")
    ),
    StateDefinition(
      name = MultipleJobsRunning.name,
      displayableName = "More than one job running",
      icon = Some(URI.create("/assets/states/error.svg")),
      tooltip = Some("More than one job running"),
      description = Some("More than one job running")
    )
  )
}
