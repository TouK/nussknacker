package pl.touk.nussknacker.ui.process.deployment.scenariostatus

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{
  DeploymentStatusDetails,
  ProcessAction,
  ProcessActionState,
  ScenarioActionName,
  StateStatus
}
import pl.touk.nussknacker.engine.deployment.DeploymentId

object InconsistentStateDetector extends InconsistentStateDetector

class InconsistentStateDetector extends LazyLogging {

  def resolveScenarioStatus(
      deploymentStatuses: List[DeploymentStatusDetails],
      lastStateAction: ProcessAction
  ): StateStatus = {
    val status = (doExtractAtMostOneStatus(deploymentStatuses), lastStateAction) match {
      case (Left(deploymentStatus), _) => deploymentStatus.status
      case (Right(None), action)
          if action.actionName == ScenarioActionName.Deploy && action.state == ProcessActionState.ExecutionFinished =>
        // Some engines like Flink have jobs retention. Because of that we restore finished status
        SimpleStateStatus.Finished
      case (Right(Some(deploymentStatus)), _) if shouldAlwaysReturnStatus(deploymentStatus) => deploymentStatus.status
      case (Right(deploymentStatusOpt), action) if action.actionName == ScenarioActionName.Deploy =>
        handleLastActionDeploy(deploymentStatusOpt, action)
      case (Right(Some(deploymentStatus)), _) if isFollowingDeployStatus(deploymentStatus) =>
        handleFollowingDeployState(deploymentStatus, lastStateAction)
      case (Right(deploymentStatusOpt), action) if action.actionName == ScenarioActionName.Cancel =>
        handleCanceledState(deploymentStatusOpt)
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
    val notFinalStatuses = deploymentStatuses.filterNot(isFinalOrTransitioningToFinalStatus)
    (deploymentStatuses, notFinalStatuses) match {
      case (Nil, Nil)                    => Right(None)
      case (_, singleNotFinished :: Nil) => Right(Some(singleNotFinished))
      case (_, firstNotFinished :: _ :: _) =>
        Left(
          firstNotFinished.copy(
            status = ProblemStateStatus.multipleJobsRunning(
              notFinalStatuses.map(deploymentStatus =>
                deploymentStatus.deploymentId.getOrElse(DeploymentId("missing")) -> deploymentStatus.status
              )
            )
          )
        )
      case (firstFinished :: _, Nil) => Right(Some(firstFinished))
    }
  }

  // This method handles some corner cases for canceled process -> with last action = Canceled
  private def handleCanceledState(deploymentStatusOpt: Option[DeploymentStatusDetails]): StateStatus =
    deploymentStatusOpt.map(_.status).getOrElse(SimpleStateStatus.Canceled)

  // This method handles some corner cases for following deploy status mismatch last action version
  private def handleFollowingDeployState(
      deploymentStatus: DeploymentStatusDetails,
      lastStateAction: ProcessAction
  ): StateStatus = {
    if (lastStateAction.actionName != ScenarioActionName.Deploy)
      ProblemStateStatus.shouldNotBeRunning(true)
    else
      deploymentStatus.status
  }

  // This method handles some corner cases for deployed action mismatch version
  private def handleLastActionDeploy(
      deploymentStatusOpt: Option[DeploymentStatusDetails],
      action: ProcessAction
  ): StateStatus =
    deploymentStatusOpt match {
      case Some(deploymentStatuses) =>
        deploymentStatuses.version match {
          case _ if !isFollowingDeployStatus(deploymentStatuses) && !isFinishedStatus(deploymentStatuses) =>
            logger.debug(
              s"handleLastActionDeploy: is not following deploy status nor finished, but it should be. $deploymentStatuses"
            )
            ProblemStateStatus.shouldBeRunning(action.processVersionId, action.user)
          case Some(ver) if ver != action.processVersionId =>
            ProblemStateStatus.mismatchDeployedVersion(ver, action.processVersionId, action.user)
          case Some(_) =>
            deploymentStatuses.status
          case None => // TODO: we should remove Option from ProcessVersion?
            ProblemStateStatus.missingDeployedVersion(action.processVersionId, action.user)
        }
      case None =>
        logger.debug(
          s"handleLastActionDeploy for empty deploymentStatus. Action.processVersionId: ${action.processVersionId}"
        )
        ProblemStateStatus.shouldBeRunning(action.processVersionId, action.user)
    }

  private def shouldAlwaysReturnStatus(deploymentStatus: DeploymentStatusDetails): Boolean = {
    ProblemStateStatus.isProblemStatus(
      deploymentStatus.status
    ) || deploymentStatus.status == SimpleStateStatus.Restarting
  }

  private def isFollowingDeployStatus(deploymentStatus: DeploymentStatusDetails): Boolean = {
    SimpleStateStatus.DefaultFollowingDeployStatuses.contains(deploymentStatus.status)
  }

  private def isFinalOrTransitioningToFinalStatus(deploymentStatus: DeploymentStatusDetails): Boolean =
    SimpleStateStatus.isFinalOrTransitioningToFinalStatus(deploymentStatus.status)

  private def isFinishedStatus(deploymentStatus: DeploymentStatusDetails): Boolean = {
    deploymentStatus.status == SimpleStateStatus.Finished
  }

}
