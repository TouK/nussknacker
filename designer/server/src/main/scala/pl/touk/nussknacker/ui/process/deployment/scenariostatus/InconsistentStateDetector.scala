package pl.touk.nussknacker.ui.process.deployment.scenariostatus

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionState, ScenarioActionName, StatusDetails}
import pl.touk.nussknacker.engine.deployment.DeploymentId

object InconsistentStateDetector extends InconsistentStateDetector

class InconsistentStateDetector extends LazyLogging {

  def resolveScenarioStatus(deploymentStatuses: List[StatusDetails], lastStateAction: ProcessAction): StatusDetails = {
    val status = (doExtractAtMostOneStatus(deploymentStatuses), lastStateAction) match {
      case (Left(deploymentStatus), _) => deploymentStatus
      case (Right(None), action)
          if action.actionName == ScenarioActionName.Deploy && action.state == ProcessActionState.ExecutionFinished =>
        // Some engines like Flink have jobs retention. Because of that we restore finished status
        StatusDetails(SimpleStateStatus.Finished, Some(DeploymentId.fromActionId(action.id)))
      case (Right(Some(deploymentStatus)), _) if shouldAlwaysReturnStatus(deploymentStatus) => deploymentStatus
      case (Right(deploymentStatusOpt), action) if action.actionName == ScenarioActionName.Deploy =>
        handleLastActionDeploy(deploymentStatusOpt, action)
      case (Right(Some(deploymentStatus)), _) if isFollowingDeployStatus(deploymentStatus) =>
        handleFollowingDeployState(deploymentStatus, lastStateAction)
      case (Right(deploymentStatusOpt), action) if action.actionName == ScenarioActionName.Cancel =>
        handleCanceledState(deploymentStatusOpt)
      case (Right(Some(deploymentStatus)), _) => deploymentStatus
      case (Right(None), a) => StatusDetails(SimpleStateStatus.NotDeployed, Some(DeploymentId.fromActionId(a.id)))
    }
    logger.debug(
      s"Resolved deployment statuses: $deploymentStatuses, lastStateAction: $lastStateAction to scenario status: $status"
    )
    status
  }

  // TODO: This method is exposed to make transition between Option[StatusDetails] and List[StatusDetails] easier to perform.
  //       After full migration to List[StatusDetails], this method should be removed
  def extractAtMostOneStatus(deploymentStatuses: List[StatusDetails]): Option[StatusDetails] =
    doExtractAtMostOneStatus(deploymentStatuses).fold(Some(_), identity)

  private def doExtractAtMostOneStatus(
      deploymentStatuses: List[StatusDetails]
  ): Either[StatusDetails, Option[StatusDetails]] = {
    val notFinalStatuses = deploymentStatuses.filterNot(isFinalOrTransitioningToFinalStatus)
    (deploymentStatuses, notFinalStatuses) match {
      case (Nil, Nil)                    => Right(None)
      case (_, singleNotFinished :: Nil) => Right(Some(singleNotFinished))
      case (_, firstNotFinished :: _ :: _) =>
        Left(
          firstNotFinished.copy(
            status = ProblemStateStatus.MultipleJobsRunning,
            errors = List(s"Expected one job, instead: ${notFinalStatuses
                .map(details => details.deploymentId.map(_.value).getOrElse("missing") + " - " + details.status)
                .mkString(", ")}")
          )
        )
      case (firstFinished :: _, Nil) => Right(Some(firstFinished))
    }
  }

  // This method handles some corner cases for canceled process -> with last action = Canceled
  private def handleCanceledState(deploymentStatusOpt: Option[StatusDetails]): StatusDetails =
    deploymentStatusOpt
      // Missing deployment is fine for cancelled action as well because of retention of states
      .getOrElse(StatusDetails(SimpleStateStatus.Canceled, None))

  // This method handles some corner cases for following deploy status mismatch last action version
  private def handleFollowingDeployState(
      deploymentStatus: StatusDetails,
      lastStateAction: ProcessAction
  ): StatusDetails = {
    if (lastStateAction.actionName != ScenarioActionName.Deploy)
      deploymentStatus.copy(status = ProblemStateStatus.shouldNotBeRunning(true))
    else
      deploymentStatus
  }

  // This method handles some corner cases for deployed action mismatch version
  private def handleLastActionDeploy(deploymentStatusOpt: Option[StatusDetails], action: ProcessAction): StatusDetails =
    deploymentStatusOpt match {
      case Some(deploymentStatuses) =>
        deploymentStatuses.version match {
          case _ if !isFollowingDeployStatus(deploymentStatuses) && !isFinishedStatus(deploymentStatuses) =>
            logger.debug(
              s"handleLastActionDeploy: is not following deploy status nor finished, but it should be. $deploymentStatuses"
            )
            deploymentStatuses.copy(status = ProblemStateStatus.shouldBeRunning(action.processVersionId, action.user))
          case Some(ver) if ver.versionId != action.processVersionId =>
            deploymentStatuses.copy(status =
              ProblemStateStatus.mismatchDeployedVersion(ver.versionId, action.processVersionId, action.user)
            )
          case Some(_) =>
            deploymentStatuses
          case None => // TODO: we should remove Option from ProcessVersion?
            deploymentStatuses.copy(status =
              ProblemStateStatus.missingDeployedVersion(action.processVersionId, action.user)
            )
        }
      case None =>
        logger.debug(
          s"handleLastActionDeploy for empty deploymentStatus. Action.processVersionId: ${action.processVersionId}"
        )
        StatusDetails(ProblemStateStatus.shouldBeRunning(action.processVersionId, action.user), None)
    }

  private def shouldAlwaysReturnStatus(deploymentStatus: StatusDetails): Boolean = {
    ProblemStateStatus.isProblemStatus(
      deploymentStatus.status
    ) || deploymentStatus.status == SimpleStateStatus.Restarting
  }

  private def isFollowingDeployStatus(deploymentStatus: StatusDetails): Boolean = {
    SimpleStateStatus.DefaultFollowingDeployStatuses.contains(deploymentStatus.status)
  }

  private def isFinalOrTransitioningToFinalStatus(deploymentStatus: StatusDetails): Boolean =
    SimpleStateStatus.isFinalOrTransitioningToFinalStatus(deploymentStatus.status)

  private def isFinishedStatus(deploymentStatus: StatusDetails): Boolean = {
    deploymentStatus.status == SimpleStateStatus.Finished
  }

}
