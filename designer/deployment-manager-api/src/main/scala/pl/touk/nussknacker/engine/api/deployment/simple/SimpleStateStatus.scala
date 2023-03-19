package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.{AllowDeployStateStatus, CustomStateStatus, DuringDeployStateStatus, FinishedStateStatus, NotEstablishedStateStatus, ProcessActionType, RunningStateStatus, StateDefinitionDetails, StateStatus}
import pl.touk.nussknacker.engine.api.process.VersionId

import java.net.URI

object SimpleStateStatus {

  // Represents general problem.
  case class ProblemStateStatus(description: String, isRedeployable: Boolean = true) extends CustomStateStatus(ProblemStateStatus.name) {
    override def isFailed: Boolean = true
  }
  case object ProblemStateStatus {
    val name: String = "PROBLEM"
    val icon: URI = URI.create("/assets/states/error.svg")
    val defaultDescription = "There are some problems with scenario."

    // Problem factory methods

    def failed: ProblemStateStatus = ProblemStateStatus(defaultDescription)

    def failedToGet: ProblemStateStatus =
      ProblemStateStatus(errorFailToGetStatus)

    def shouldBeRunning(deployedVersionId: VersionId, user: String): ProblemStateStatus =
      ProblemStateStatus(shouldBeRunningTooltip(deployedVersionId.value, user))

    def mismatchDeployedVersion(deployedVersionId: VersionId, exceptedVersionId: VersionId, user: String): ProblemStateStatus =
      ProblemStateStatus(mismatchDeployedVersionTooltip(deployedVersionId.value, exceptedVersionId.value, user))

    def shouldNotBeRunning(deployed: Boolean): ProblemStateStatus =
      ProblemStateStatus(shouldNotBeRunningMessage(deployed))

    def missingDeployedVersion(exceptedVersionId: VersionId, user: String): ProblemStateStatus =
      ProblemStateStatus(missingDeployedVersionTooltip(exceptedVersionId.value, user))

    def processWithoutAction: ProblemStateStatus =
      ProblemStateStatus(processWithoutActionMessage)

    def multipleJobsRunning: ProblemStateStatus =
      ProblemStateStatus(errorMultipleJobsMessage, isRedeployable = false)

    // Problem descriptions

    private def errorFailToGetStatus: String =
      s"Failed to get a state of the scenario."

    private def shouldBeRunningTooltip(deployedVersionId: Long, user: String): String =
      s"Scenario deployed in version $deployedVersionId (by $user), should be running."

    private def mismatchDeployedVersionTooltip(deployedVersionId: Long, exceptedVersionId: Long, user: String): String =
      s"Scenario deployed in version $deployedVersionId (by $user), expected version $exceptedVersionId!"

    private def missingDeployedVersionTooltip(exceptedVersionId: Long, user: String): String =
      s"Scenario deployed without version (by $user), expected version $exceptedVersionId."

    private def processWithoutActionMessage: String =
      "Scenario state error - no actions found."

    private def shouldNotBeRunningMessage(deployed: Boolean): String =
      if (deployed) "Scenario has been canceled but still is running."
      else "Scenario has been never deployed but now is running."

    private def errorMultipleJobsMessage: String =
      "More than one deployment is running."
  }

  val NotDeployed: StateStatus = AllowDeployStateStatus("NOT_DEPLOYED")
  val DuringDeploy: StateStatus = DuringDeployStateStatus("DURING_DEPLOY")
  val Running: StateStatus = RunningStateStatus("RUNNING")
  val Finished: StateStatus = FinishedStateStatus("FINISHED")
  val Restarting: StateStatus = NotEstablishedStateStatus("RESTARTING")
  val DuringCancel: StateStatus = NotEstablishedStateStatus("DURING_CANCEL")
  val Canceled: StateStatus = AllowDeployStateStatus("CANCELED")

  val statusActionsPF: PartialFunction[StateStatus, List[ProcessActionType]] = {
    case SimpleStateStatus.NotDeployed => List(ProcessActionType.Deploy, ProcessActionType.Archive)
    case SimpleStateStatus.DuringDeploy => List(ProcessActionType.Deploy, ProcessActionType.Cancel)
    case SimpleStateStatus.Running => List(ProcessActionType.Cancel, ProcessActionType.Pause, ProcessActionType.Deploy)
    case SimpleStateStatus.Canceled => List(ProcessActionType.Deploy, ProcessActionType.Archive)
    case SimpleStateStatus.Restarting => List(ProcessActionType.Deploy, ProcessActionType.Cancel)
    case SimpleStateStatus.Finished => List(ProcessActionType.Deploy, ProcessActionType.Archive)
    // When Failed - process is in terminal state in Flink and it doesn't require any cleanup in Flink, but in NK it does
    // - that's why Cancel action is available
    case SimpleStateStatus.ProblemStateStatus(_, true) => List(ProcessActionType.Deploy, ProcessActionType.Cancel)
    case SimpleStateStatus.ProblemStateStatus(_, _) => List(ProcessActionType.Cancel)
  }

  val definitions: Map[StatusName, StateDefinitionDetails] = Map(
    SimpleStateStatus.ProblemStateStatus.name -> StateDefinitionDetails(
      displayableName = "Problem",
      icon = Some(ProblemStateStatus.icon),
      tooltip = Some(ProblemStateStatus.defaultDescription),
      description = Some(ProblemStateStatus.defaultDescription)
    ),
    SimpleStateStatus.NotDeployed.name -> StateDefinitionDetails(
      displayableName = "Not deployed",
      icon = Some(URI.create("/assets/states/not-deployed.svg")),
      tooltip = Some("The scenario is not deployed."),
      description = Some("The scenario is not deployed.")
    ),
    SimpleStateStatus.DuringDeploy.name -> StateDefinitionDetails(
      displayableName = "During deploy",
      icon = Some(URI.create("/assets/states/deploy-running.svg")),
      tooltip = Some("The scenario has been already started and currently is being deployed."),
      description = Some("The scenario is being deployed.")
    ),
    SimpleStateStatus.Running.name -> StateDefinitionDetails(
      displayableName = "Running",
      icon = Some(URI.create("/assets/states/deploy-success.svg")),
      tooltip = Some("The scenario has been successfully deployed and currently is running."),
      description = Some("The scenario is running.")
    ),
    SimpleStateStatus.Canceled.name -> StateDefinitionDetails(
      displayableName = "Canceled",
      icon = Some(URI.create("/assets/states/stopping-success.svg")),
      tooltip = Some("The scenario has been successfully cancelled."),
      description = Some("The scenario is canceled.")
    ),
    SimpleStateStatus.Restarting.name -> StateDefinitionDetails(
      displayableName = "Restarting",
      icon = Some(URI.create("/assets/states/deploy-restart.svg")),
      tooltip = Some("Scenario was deployed but now is restarting..."),
      description = Some("Scenario is restarting...")
    ),
    SimpleStateStatus.DuringCancel.name -> StateDefinitionDetails(
      displayableName = "During cancel",
      icon = Some(URI.create("/assets/states/stopping-running.svg")),
      tooltip = Some("The scenario currently is being canceled."),
      description = Some("The scenario is being canceled.")
    ),
    SimpleStateStatus.Finished.name -> StateDefinitionDetails(
      displayableName = "Finished",
      icon = Some(URI.create("/assets/states/success.svg")),
      tooltip = Some("The scenario completed successfully."),
      description = Some("The scenario has finished.")
    ),
  )
}
