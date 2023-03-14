package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
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

  val customStateDefinitions: Map[StatusName, StateDefinitionDetails] = Map(
    Failing.name -> StateDefinitionDetails(
      displayableName = "Failing",
      icon = Some(URI.create("/assets/states/error.svg")),
      tooltip = Some("Failing"),
      description = Some("Failing")
    ),
    MultipleJobsRunning.name -> StateDefinitionDetails(
      displayableName = "More than one deployment running",
      icon = Some(URI.create("/assets/states/error.svg")),
      tooltip = Some("More than one deployment running"),
      description = Some("More than one deployment running")
    )
  )
}
