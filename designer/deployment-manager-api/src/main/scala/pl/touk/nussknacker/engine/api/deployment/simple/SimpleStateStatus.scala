package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus.{
  GeneralProblemStateStatus,
  MultipleJobsRunning,
  ShouldNotBeRunning
}
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.deployment.DeploymentId

import java.net.URI

object SimpleStateStatus {

  def fromDeploymentStatus(deploymentStatus: DeploymentStatus): StateStatus = {
    deploymentStatus match {
      case status: NoAttributesDeploymentStatus => NoAttributesStateStatus(status.name.value)
      // We assume that all deployment status have default allowedActions. Non-default allowedActions have only
      // statuses that are not deployment statuses but scenario statuses.
      case status: ProblemDeploymentStatus => GeneralProblemStateStatus(status.problemDescription)
    }
  }

  sealed trait ProblemStateStatus extends StateStatus {
    override def name: StatusName = ProblemStateStatus.name

    def description: String
    def allowedActions: Set[ScenarioActionName]
    def tooltip: Option[String]
  }

  object ProblemStateStatus {

    final case class ShouldNotBeRunning(deployed: Boolean) extends ProblemStateStatus {

      override val description: String =
        if (deployed) "Scenario has been canceled but still is running."
        else "Scenario has been never deployed but now is running."

      override val allowedActions: Set[ScenarioActionName] = defaultActions
      override val tooltip: Option[String]                 = None
    }

    final case class MultipleJobsRunning(
        firstNonFinalDeployment: (DeploymentId, StateStatus),
        secondNonFinalDeployment: (DeploymentId, StateStatus),
        otherNonFinalDeployments: (DeploymentId, StateStatus)*
    ) extends ProblemStateStatus {

      override val allowedActions: Set[ScenarioActionName] = Set(ScenarioActionName.Cancel)
      override val description: String                     = "More than one deployment is running."

      override val tooltip: Option[StatusName] = Some {
        (firstNonFinalDeployment :: secondNonFinalDeployment :: otherNonFinalDeployments.toList)
          .map { case (deploymentId, deploymentStatus) => s"$deploymentId - $deploymentStatus" }
          .mkString("Expected one job, instead: ", ", ", "")
      }

    }

    final case class GeneralProblemStateStatus(
        override val description: String,
        override val allowedActions: Set[ScenarioActionName] = defaultActions,
        override val tooltip: Option[String] = None
    ) extends ProblemStateStatus

    val name: String = "PROBLEM"

    def isProblemStatus(status: StateStatus): Boolean = status.name == name

    val icon: URI          = URI.create("/assets/states/error.svg")
    val defaultDescription = "There are some problems with scenario."
    val defaultActions: Set[ScenarioActionName] =
      Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)

    // Problem factory methods

    val Failed: ProblemStateStatus = GeneralProblemStateStatus(defaultDescription)

    val ArchivedShouldBeCanceled: ProblemStateStatus =
      GeneralProblemStateStatus("Archived scenario should be canceled.", Set(ScenarioActionName.Cancel))

    val FailedToGet: ProblemStateStatus =
      GeneralProblemStateStatus(s"Failed to get a state of the scenario.")

    def shouldBeRunning(deployedVersionId: VersionId, user: String): ProblemStateStatus =
      GeneralProblemStateStatus(s"Scenario deployed in version $deployedVersionId by $user is not running.")

    def mismatchDeployedVersion(
        deployedVersionId: VersionId,
        exceptedVersionId: VersionId,
        user: String
    ): ProblemStateStatus =
      GeneralProblemStateStatus(
        s"Scenario deployed in version $deployedVersionId by $user, expected version $exceptedVersionId."
      )

    def shouldNotBeRunning(deployed: Boolean): ProblemStateStatus =
      ShouldNotBeRunning(deployed)

    def missingDeployedVersion(exceptedVersionId: VersionId, user: String): ProblemStateStatus =
      GeneralProblemStateStatus(s"Scenario deployed without version by $user, expected version $exceptedVersionId.")

    def multipleJobsRunning(
        first: (DeploymentId, StateStatus),
        second: (DeploymentId, StateStatus),
        others: (DeploymentId, StateStatus)*
    ): ProblemStateStatus =
      MultipleJobsRunning(first, second, others: _*)

  }

  implicit class CanBeConsideredAsActiveStatus(val status: StateStatus) extends AnyVal {

    def isActive: Boolean = {
      status match {
        case _: ShouldNotBeRunning        => true
        case _: MultipleJobsRunning       => true
        case _: GeneralProblemStateStatus => false
        case `Restarting`                 => true
        case status                       => DefaultFollowingDeployStatuses.contains(status)
      }
    }

  }

  val NotDeployed: StateStatus  = StateStatus("NOT_DEPLOYED")
  val DuringDeploy: StateStatus = StateStatus("DURING_DEPLOY")
  val Running: StateStatus      = StateStatus("RUNNING")
  val Finished: StateStatus     = StateStatus("FINISHED")
  val Restarting: StateStatus   = StateStatus("RESTARTING")
  val DuringCancel: StateStatus = StateStatus("DURING_CANCEL")
  val Canceled: StateStatus     = StateStatus("CANCELED")

  val DefaultFollowingDeployStatuses: Set[StateStatus] = Set(DuringDeploy, Running)

  def isFinalOrTransitioningToFinalStatus(status: StateStatus): Boolean =
    List(SimpleStateStatus.Finished, SimpleStateStatus.DuringCancel, SimpleStateStatus.Canceled).contains(
      status
    ) || ProblemStateStatus.isProblemStatus(
      status
    )

  val statusActionsPF: PartialFunction[StateStatus, Set[ScenarioActionName]] = {
    case SimpleStateStatus.NotDeployed =>
      Set(ScenarioActionName.Deploy, ScenarioActionName.Archive, ScenarioActionName.Rename)
    case SimpleStateStatus.DuringDeploy =>
      Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    case SimpleStateStatus.Running =>
      Set(ScenarioActionName.Cancel, ScenarioActionName.Pause, ScenarioActionName.Deploy)
    case SimpleStateStatus.Canceled =>
      Set(ScenarioActionName.Deploy, ScenarioActionName.Archive, ScenarioActionName.Rename)
    case SimpleStateStatus.Restarting =>
      Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    case SimpleStateStatus.Finished =>
      Set(ScenarioActionName.Deploy, ScenarioActionName.Archive, ScenarioActionName.Rename)
    case SimpleStateStatus.DuringCancel =>
      Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    // When Failed - process is in terminal state in Flink and it doesn't require any cleanup in Flink, but in NK it does
    // - that's why Cancel action is available
    case s: ShouldNotBeRunning                           => s.allowedActions
    case GeneralProblemStateStatus(_, allowedActions, _) => allowedActions
  }

  val definitions: Map[StatusName, StateDefinitionDetails] = Map(
    SimpleStateStatus.ProblemStateStatus.name -> StateDefinitionDetails(
      displayableName = "Problem",
      icon = ProblemStateStatus.icon,
      tooltip = ProblemStateStatus.defaultDescription,
      description = ProblemStateStatus.defaultDescription
    ),
    SimpleStateStatus.NotDeployed.name -> StateDefinitionDetails(
      displayableName = "Not deployed",
      icon = URI.create("/assets/states/not-deployed.svg"),
      tooltip = "The scenario is not deployed.",
      description = "The scenario is not deployed."
    ),
    SimpleStateStatus.DuringDeploy.name -> StateDefinitionDetails(
      displayableName = "During deploy",
      icon = URI.create("/assets/states/deploy-running-animated.svg"),
      tooltip = "The scenario has been already started and currently is being deployed.",
      description = "The scenario is being deployed."
    ),
    SimpleStateStatus.Running.name -> StateDefinitionDetails(
      displayableName = "Running",
      icon = URI.create("/assets/states/deploy-success.svg"),
      tooltip = "The scenario has been successfully deployed and currently is running.",
      description = "The scenario is running."
    ),
    SimpleStateStatus.Canceled.name -> StateDefinitionDetails(
      displayableName = "Canceled",
      icon = URI.create("/assets/states/stopping-success.svg"),
      tooltip = "The scenario has been successfully cancelled.",
      description = "The scenario is canceled."
    ),
    SimpleStateStatus.Restarting.name -> StateDefinitionDetails(
      displayableName = "Restarting",
      icon = URI.create("/assets/states/deploy-restart-animated.svg"),
      tooltip = "Scenario was deployed but now is restarting...",
      description = "Scenario is restarting..."
    ),
    SimpleStateStatus.DuringCancel.name -> StateDefinitionDetails(
      displayableName = "During cancel",
      icon = URI.create("/assets/states/stopping-running-animated.svg"),
      tooltip = "The scenario currently is being canceled.",
      description = "The scenario is being canceled."
    ),
    SimpleStateStatus.Finished.name -> StateDefinitionDetails(
      displayableName = "Finished",
      icon = URI.create("/assets/states/success.svg"),
      tooltip = "The scenario completed successfully.",
      description = "The scenario has finished."
    ),
  )

}
