package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.{AllowDeployStateStatus, DuringDeployStateStatus, FailedStateStatus, FinishedStateStatus, NotEstablishedStateStatus, ProcessActionType, RunningStateStatus, StateDefinition, StateStatus}

import java.net.URI

object SimpleStateStatus {
  val FailedToGet: StateStatus = NotEstablishedStateStatus("FAILED_TO_GET")
  val Unknown: StateStatus = NotEstablishedStateStatus("UNKNOWN")
  val NotDeployed: StateStatus = AllowDeployStateStatus("NOT_DEPLOYED")
  val DuringDeploy: StateStatus = DuringDeployStateStatus("DURING_DEPLOY")
  val Running: StateStatus = RunningStateStatus("RUNNING")
  val Finished: StateStatus = FinishedStateStatus("FINISHED")
  val Restarting: StateStatus = NotEstablishedStateStatus("RESTARTING")
  val DuringCancel: StateStatus = NotEstablishedStateStatus("DURING_CANCEL")
  val Canceled: StateStatus = AllowDeployStateStatus("CANCELED")
  val Failed: StateStatus = FailedStateStatus("FAILED")
  val Error: StateStatus = AllowDeployStateStatus("ERROR")
  val Warning: StateStatus = AllowDeployStateStatus("WARNING")

  val defaultActions: List[ProcessActionType] = Nil

  val actionStatusMap: Map[ProcessActionType, StateStatus] = Map(
    ProcessActionType.Deploy -> SimpleStateStatus.Running,
    ProcessActionType.Cancel -> SimpleStateStatus.Canceled,
    ProcessActionType.Archive -> SimpleStateStatus.NotDeployed,
    ProcessActionType.UnArchive -> SimpleStateStatus.NotDeployed
  )

  val statusActionsMap: Map[StateStatus, List[ProcessActionType]] = Map(
    SimpleStateStatus.Unknown -> List(ProcessActionType.Deploy),
    SimpleStateStatus.NotDeployed -> List(ProcessActionType.Deploy, ProcessActionType.Archive),
    SimpleStateStatus.DuringDeploy -> List(ProcessActionType.Deploy, ProcessActionType.Cancel), // Deploy? see FlinkStateStatus
    SimpleStateStatus.Running -> List(ProcessActionType.Cancel, ProcessActionType.Pause, ProcessActionType.Deploy),
    SimpleStateStatus.Canceled -> List(ProcessActionType.Deploy, ProcessActionType.Archive),
    SimpleStateStatus.Restarting -> List(ProcessActionType.Deploy, ProcessActionType.Cancel), // Deploy? see FlinkStateStatus
    SimpleStateStatus.Finished -> List(ProcessActionType.Deploy, ProcessActionType.Archive),
    // When Failed - process is in terminal state in Flink and it doesn't require any cleanup in Flink, but in NK it does
    // - that's why Cancel action is available
    SimpleStateStatus.Failed -> List(ProcessActionType.Deploy, ProcessActionType.Cancel),
    SimpleStateStatus.Error -> List(ProcessActionType.Deploy, ProcessActionType.Cancel),
    SimpleStateStatus.Warning -> List(ProcessActionType.Deploy, ProcessActionType.Cancel),
    SimpleStateStatus.FailedToGet -> List(ProcessActionType.Deploy, ProcessActionType.Archive)
  )

  val definitions: Map[StatusName, StateDefinition] = Map(
    SimpleStateStatus.FailedToGet.name -> StateDefinition(
      displayableName = "Failed to get",
      icon = Some(URI.create("/assets/states/error.svg")),
      tooltip = Some("There are problems obtaining the scenario state. Please check if your engine is working properly."),
      description = Some("Failed to get a state of the scenario.")
    ),
    SimpleStateStatus.Unknown.name -> StateDefinition(
      displayableName = "Unknown",
      icon = Some(URI.create("/assets/states/status-unknown.svg")),
      tooltip = Some("Unknown state of the scenario. We can't recognize scenario state."),
      description = Some("Unknown state of the scenario.")
    ),
    SimpleStateStatus.NotDeployed.name -> StateDefinition(
      displayableName = "Not deployed",
      icon = Some(URI.create("/assets/states/not-deployed.svg")),
      tooltip = Some("The scenario is not deployed."),
      description = Some("The scenario is not deployed.")
    ),
    SimpleStateStatus.DuringDeploy.name -> StateDefinition(
      displayableName = "During deploy",
      icon = Some(URI.create("/assets/states/deploy-running-animated.svg")),
      tooltip = Some("The scenario has been already started and currently is being deployed."),
      description = Some("The scenario is being deployed.")
    ),
    SimpleStateStatus.Running.name -> StateDefinition(
      displayableName = "Running",
      icon = Some(URI.create("/assets/states/deploy-success.svg")),
      tooltip = Some("The scenario has been successfully deployed and currently is running."),
      description = Some("The scenario is running.")
    ),
    SimpleStateStatus.Canceled.name -> StateDefinition(
      displayableName = "Canceled",
      icon = Some(URI.create("/assets/states/stopping-success.svg")),
      tooltip = Some("The scenario has been successfully cancelled."),
      description = Some("The scenario is canceled.")
    ),
    SimpleStateStatus.Restarting.name -> StateDefinition(
      displayableName = "Restarting",
      icon = Some(URI.create("/assets/states/deploy-restart-animated.svg")),
      tooltip = Some("Scenario was deployed but now is restarting..."),
      description = Some("Scenario is restarting...")
    ),
    SimpleStateStatus.DuringCancel.name -> StateDefinition(
      displayableName = "During cancel",
      icon = Some(URI.create("/assets/states/stopping-running-animated.svg")),
      tooltip = Some("The scenario currently is being canceled."),
      description = Some("The scenario is being canceled.")
    ),
    SimpleStateStatus.Failed.name -> StateDefinition(
      displayableName = "Failed",
      icon = Some(URI.create("/assets/states/failed.svg")),
      tooltip = Some("There are some problems with scenario."),
      description = Some("There are some problems with scenario.")
    ),
    SimpleStateStatus.Finished.name -> StateDefinition(
      displayableName = "Finished",
      icon = Some(URI.create("/assets/states/success.svg")),
      tooltip = Some("The scenario completed successfully."),
      description = Some("The scenario has finished.")
    ),
    SimpleStateStatus.Error.name -> StateDefinition(
      displayableName = "Error",
      icon = Some(URI.create("/assets/states/error.svg")),
      tooltip = Some("There are some errors. Please check if everything is okay with scenario!"),
      description = Some("There are errors establishing a scenario state.")
    ),
    SimpleStateStatus.Warning.name -> StateDefinition(
      displayableName = "Warning",
      icon = Some(URI.create("/assets/states/warning.svg")),
      tooltip = Some("There are some warnings. Please check if everything is okay with scenario!"),
      description = Some("There are some warnings establishing a scenario state.")
    )
  )
}
