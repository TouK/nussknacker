package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.{AllowDeployStateStatus, DuringDeployStateStatus, FailedStateStatus, FinishedStateStatus, NotEstablishedStateStatus, RunningStateStatus, StateDefinition, StateStatus}

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

  val definitions: Set[StateDefinition] = Set(
    StateDefinition(
      name = SimpleStateStatus.FailedToGet.name,
      displayableName = "Failed to get",
      icon = Some(URI.create("/assets/states/error.svg")),
      tooltip = Some("There are problems obtaining the scenario state. Please check if your engine is working properly."),
      description = Some("Failed to get a state of the scenario.")
    ),
    StateDefinition(
      name = SimpleStateStatus.Unknown.name,
      displayableName = "Unknown",
      icon = Some(URI.create("/assets/states/status-unknown.svg")),
      tooltip = Some("Unknown state of the scenario. We can't recognize scenario state."),
      description = Some("Unknown state of the scenario.")
    ),
    StateDefinition(
      name = SimpleStateStatus.NotDeployed.name,
      displayableName = "Not deployed",
      icon = Some(URI.create("/assets/states/not-deployed.svg")),
      tooltip = Some("The scenario is not deployed."),
      description = Some("The scenario is not deployed.")
    ),
    StateDefinition(
      name = SimpleStateStatus.DuringDeploy.name,
      displayableName = "During deploy",
      icon = Some(URI.create("/assets/states/deploy-running-animated.svg")),
      tooltip = Some("The scenario has been already started and currently is being deployed."),
      description = Some("The scenario is being deployed.")
    ),
    StateDefinition(
      name = SimpleStateStatus.Running.name,
      displayableName = "Running",
      icon = Some(URI.create("/assets/states/deploy-success.svg")),
      tooltip = Some("The scenario has been successfully deployed and currently is running."),
      description = Some("The scenario is running.")
    ),
    StateDefinition(
      name = SimpleStateStatus.Canceled.name,
      displayableName = "Canceled",
      icon = Some(URI.create("/assets/states/stopping-success.svg")),
      tooltip = Some("The scenario has been successfully cancelled."),
      description = Some("The scenario is canceled.")
    ),
    StateDefinition(
      name = SimpleStateStatus.Restarting.name,
      displayableName = "Restarting",
      icon = Some(URI.create("/assets/states/deploy-restart-animated.svg")),
      tooltip = Some("Scenario was deployed but now is restarting..."),
      description = Some("Scenario is restarting...")
    ),
    StateDefinition(
      name = SimpleStateStatus.DuringCancel.name,
      displayableName = "During cancel",
      icon = Some(URI.create("/assets/states/stopping-running-animated.svg")),
      tooltip = Some("The scenario currently is being canceled."),
      description = Some("The scenario is being canceled.")
    ),
    StateDefinition(
      name = SimpleStateStatus.Failed.name,
      displayableName = "Failed",
      icon = Some(URI.create("/assets/states/failed.svg")),
      tooltip = Some("There are some problems with scenario."),
      description = Some("There are some problems with scenario.")
    ),
    StateDefinition(
      name = SimpleStateStatus.Finished.name,
      displayableName = "Finished",
      icon = Some(URI.create("/assets/states/success.svg")),
      tooltip = Some("The scenario completed successfully."),
      description = Some("The scenario has finished.")
    ),
    StateDefinition(
      name = SimpleStateStatus.Error.name,
      displayableName = "Error",
      icon = Some(URI.create("/assets/states/error.svg")),
      tooltip = Some("There are some errors. Please check if everything is okay with scenario!"),
      description = Some("There are errors establishing a scenario state.")
    ),
    StateDefinition(
      name = SimpleStateStatus.Warning.name,
      displayableName = "Warning",
      icon = Some(URI.create("/assets/states/warning.svg")),
      tooltip = Some("There are some warnings. Please check if everything is okay with scenario!"),
      description = Some("There are some warnings establishing a scenario state.")
    )
  )
}
