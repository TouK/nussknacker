package pl.touk.nussknacker.ui.process.deployment.scenariostatus

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.{
  DuringDeploy,
  DuringRedeploy,
  Finished,
  ProblemStateStatus,
  Running
}
import pl.touk.nussknacker.engine.deployment.DeploymentId

object InconsistentStateDetector extends InconsistentStateDetector

class InconsistentStateDetector extends LazyLogging {

  def resolveScenarioStatus(
      deploymentStatuses: List[DeploymentStatusDetails],
      lastStateAction: ScenarioStatusActionDetails
  ): StateStatus = {
    val status: StateStatus = (doExtractAtMostOneStatus(deploymentStatuses), lastStateAction) match {
      case (Left(deploymentStatus), _) => deploymentStatus.status
      case (Right(None), lastAction)
          if Set(ScenarioActionName.Deploy, ScenarioActionName.Redeploy).contains(
            lastAction.actionName
          ) && lastAction.state == ProcessActionState.ExecutionFinished =>
        // Some engines like Flink have jobs retention. Because of that we restore finished status
        SimpleStateStatus.Finished(lastAction.processVersionId)
      case (Right(Some(deploymentStatus)), _) if shouldAlwaysReturnStatus(deploymentStatus) => deploymentStatus.status
      case (Right(deploymentStatusOpt), lastAction)
          if Set(ScenarioActionName.Deploy, ScenarioActionName.Redeploy).contains(lastAction.actionName) =>
        handleLastActionDeployOrRedeploy(deploymentStatusOpt, lastAction)
      case (Right(Some(deploymentStatus)), _) if isFollowingDeployStatus(deploymentStatus) =>
        handleFollowingDeployEngineSideStatus(deploymentStatus, lastStateAction)
      case (Right(deploymentStatusOpt), lastAction) if lastAction.actionName == ScenarioActionName.Cancel =>
        handleLastActionCancel(deploymentStatusOpt)
      case (Right(Some(deploymentStatus)), _) => deploymentStatus.status
      case (Right(None), _)                   => SimpleStateStatus.NotDeployed
    }
    logger.debug(
      s"Resolved deployment statuses: $deploymentStatuses, lastStateAction: $lastStateAction to scenario status: $status"
    )
    status
  }

  private[scenariostatus] def extractAtMostOneStatus(
      deploymentStatuses: List[DeploymentStatusDetails]
  ): Option[DeploymentStatusDetails] =
    doExtractAtMostOneStatus(deploymentStatuses).fold(Some(_), identity)

  private def doExtractAtMostOneStatus(
      deploymentStatuses: List[DeploymentStatusDetails]
  ): Either[DeploymentStatusDetails, Option[DeploymentStatusDetails]] = {
    def deploymentIfFrom(details: DeploymentStatusDetails) =
      details.deploymentId.getOrElse(DeploymentId("missing"))
    val notFinalStatuses = deploymentStatuses.filterNot(isFinalOrTransitioningToFinalStatus)
    (deploymentStatuses, notFinalStatuses) match {
      case (Nil, Nil)                    => Right(None)
      case (_, singleNotFinished :: Nil) => Right(Some(singleNotFinished))
      case (_, firstNotFinished :: secondNotFinished :: otherNotFinished) =>
        Left(
          firstNotFinished.copy(
            status = ProblemStateStatus.multipleJobsRunning(
              deploymentIfFrom(firstNotFinished)  -> firstNotFinished.status,
              deploymentIfFrom(secondNotFinished) -> firstNotFinished.status,
              otherNotFinished.map(d => deploymentIfFrom(d) -> firstNotFinished.status): _*
            )
          )
        )
      case (firstFinished :: _, Nil) => Right(Some(firstFinished))
    }
  }

  // This method handles some corner cases for canceled process -> with last action = Canceled
  private def handleLastActionCancel(deploymentStatusOpt: Option[DeploymentStatusDetails]): StateStatus =
    deploymentStatusOpt
      .map(_.status)
      .getOrElse(SimpleStateStatus.Canceled)

  // This method handles some corner cases for following deploy status mismatch last action version
  private def handleFollowingDeployEngineSideStatus(
      deploymentStatus: DeploymentStatusDetails,
      lastStateAction: ScenarioStatusActionDetails
  ): StateStatus = {
    if (!List(ScenarioActionName.Deploy, ScenarioActionName.Redeploy).contains(lastStateAction.actionName))
      ProblemStateStatus.shouldNotBeRunning(true)
    else
      deploymentStatus.status
  }

  // This method handles some corner cases for deployed action mismatch version
  private def handleLastActionDeployOrRedeploy(
      deploymentStatusOpt: Option[DeploymentStatusDetails],
      action: ScenarioStatusActionDetails
  ): StateStatus =
    deploymentStatusOpt match {
      case Some(deploymentStatuses) =>
        deploymentStatuses.status match {
          case _ if !isFollowingDeployStatus(deploymentStatuses) && !isFinishedStatus(deploymentStatuses) =>
            logger.debug(
              s"handleLastActionDeployOrRedeploy: is not following deploy status nor finished, but it should be. $deploymentStatuses"
            )
            ProblemStateStatus.shouldBeRunning(action.processVersionId, action.user)
          case DuringDeploy(ver) if ver != action.processVersionId =>
            ProblemStateStatus.mismatchDeployedVersion(ver, action.processVersionId, action.user)
          case DuringRedeploy(ver) if ver != action.processVersionId =>
            ProblemStateStatus.mismatchDeployedVersion(ver, action.processVersionId, action.user)
          case Running(ver, _) if ver != action.processVersionId =>
            ProblemStateStatus.mismatchDeployedVersion(ver, action.processVersionId, action.user)
          case SimpleStateStatus.DuringDeploy(processVersion) if action.actionName == ScenarioActionName.Redeploy =>
            SimpleStateStatus.DuringRedeploy(processVersion)
          case other =>
            other
        }
      case None =>
        logger.debug(
          s"handleLastActionDeployOrRedeploy for empty deploymentStatus. Action.processVersionId: ${action.processVersionId}"
        )
        ProblemStateStatus.shouldBeRunning(action.processVersionId, action.user)
    }

  private def shouldAlwaysReturnStatus(deploymentStatus: DeploymentStatusDetails): Boolean = {
    ProblemStateStatus.isProblemStatus(
      deploymentStatus.status
    ) || deploymentStatus.status == SimpleStateStatus.Restarting
  }

  private def isFollowingDeployStatus(deploymentStatus: DeploymentStatusDetails): Boolean = {
    SimpleStateStatus.isDefaultFollowingDeployStatus(deploymentStatus.status)
  }

  private def isFinalOrTransitioningToFinalStatus(deploymentStatus: DeploymentStatusDetails): Boolean =
    SimpleStateStatus.isFinalOrTransitioningToFinalStatus(deploymentStatus.status)

  private def isFinishedStatus(deploymentStatus: DeploymentStatusDetails): Boolean = {
    SimpleStateStatus.isFinished(deploymentStatus.status)
  }

}
