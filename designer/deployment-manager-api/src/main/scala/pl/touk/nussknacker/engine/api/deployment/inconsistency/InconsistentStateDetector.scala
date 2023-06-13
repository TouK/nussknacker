package pl.touk.nussknacker.engine.api.deployment.inconsistency

import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, StatusDetails}

object InconsistentStateDetector extends InconsistentStateDetector

class InconsistentStateDetector {

  //This method handles some corner cases like retention for keeping old states - some engine can cleanup canceled states. It's more Flink hermetic.
  def resolve(statusDetails: List[StatusDetails], lastStateAction: Option[ProcessAction]): StatusDetails =
    (doExtractAtMostOneStatus(statusDetails), lastStateAction) match {
      case (Left(state), _) => state
      case (Right(Some(state)), _) if shouldAlwaysReturnStatus(state) => state
      case (Right(Some(state)), _) if state.status == SimpleStateStatus.Restarting => handleRestartingState(state, lastStateAction)
      case (Right(statusDetailsOpt), Some(action)) if action.isDeployed => handleMismatchDeployedStateLastAction(statusDetailsOpt, action)
      case (Right(Some(state)), _) if isFollowingDeployStatus(state) => handleFollowingDeployState(state, lastStateAction)
      case (Right(statusDetailsOpt), Some(action)) if action.isCanceled => handleCanceledState(statusDetailsOpt)
      case (Right(Some(state)), _) => handleState(state, lastStateAction)
      case (Right(None), Some(_)) => StatusDetails(SimpleStateStatus.NotDeployed)
      case (Right(None), None) => StatusDetails(SimpleStateStatus.NotDeployed)
    }

  // TODO: This method is exposed to make transition between Option[StatusDetails] and List[StatusDetails] easier to perform.
  //       After full migration to List[StatusDetails], this method should be removed
  def extractAtMostOneStatus(statusDetails: List[StatusDetails]): Option[StatusDetails] =
    doExtractAtMostOneStatus(statusDetails).fold(Some(_), identity)

  private def doExtractAtMostOneStatus(statusDetails: List[StatusDetails]): Either[StatusDetails, Option[StatusDetails]] = {
    val notFinishedStatuses = statusDetails.filterNot(isFinished)
    (statusDetails, notFinishedStatuses) match {
      case (Nil, Nil) => Right(None)
      case (_, singleNotFinished :: Nil) => Right(Some(singleNotFinished))
      case (_, firstNotFinished :: _ :: _) =>
        Left(firstNotFinished.copy(
          status = ProblemStateStatus.MultipleJobsRunning,
          errors = List(s"Expected one job, instead: ${notFinishedStatuses.map(details => details.deploymentId.map(_.value).getOrElse("missing") + " - " + details.status).mkString(", ")}")))
      case (firstFinished :: _, Nil) => Right(Some(firstFinished))
    }
  }

  private def handleState(statusDetails: StatusDetails, lastStateAction: Option[ProcessAction]): StatusDetails =
    statusDetails.status match {
      case SimpleStateStatus.NotDeployed if lastStateAction.isEmpty =>
        statusDetails.copy(status = SimpleStateStatus.NotDeployed)
      case SimpleStateStatus.Restarting | SimpleStateStatus.DuringCancel | SimpleStateStatus.Finished if lastStateAction.isEmpty =>
        statusDetails.copy(status = ProblemStateStatus.ProcessWithoutAction)
      case _ => statusDetails
    }

  //Thise method handles some corner cases for canceled process -> with last action = Canceled
  private def handleCanceledState(statusDetails: Option[StatusDetails]): StatusDetails =
    statusDetails match {
      case Some(state) => state.status match {
        case _ => state
      }
      case None => StatusDetails(SimpleStateStatus.Canceled)
    }

  private def handleRestartingState(statusDetails: StatusDetails, lastStateAction: Option[ProcessAction]): StatusDetails =
    lastStateAction match {
      case Some(action) if action.isDeployed => statusDetails
      case _ => handleState(statusDetails, lastStateAction)
    }

  //This method handles some corner cases for following deploy state mismatch last action version
  private def handleFollowingDeployState(statusDetails: StatusDetails, lastStateAction: Option[ProcessAction]): StatusDetails =
    lastStateAction match {
      case Some(action) if !action.isDeployed =>
        statusDetails.copy(status = ProblemStateStatus.shouldNotBeRunning(true))
      case Some(_) =>
        statusDetails
      case None =>
        statusDetails.copy(status = ProblemStateStatus.shouldNotBeRunning(false))
    }

  //This method handles some corner cases for deployed action mismatch state version
  private def handleMismatchDeployedStateLastAction(statusDetails: Option[StatusDetails], action: ProcessAction): StatusDetails =
    statusDetails match {
      case Some(state) =>
        state.version match {
          case _ if !(isFollowingDeployStatus(state)) =>
            state.copy(status = ProblemStateStatus.shouldBeRunning(action.processVersionId, action.user))
          case Some(ver) if ver.versionId != action.processVersionId =>
            state.copy(status = ProblemStateStatus.mismatchDeployedVersion(ver.versionId, action.processVersionId, action.user))
          case Some(ver) if ver.versionId == action.processVersionId =>
            state
          case None => //TODO: we should remove Option from ProcessVersion?
            state.copy(status = ProblemStateStatus.missingDeployedVersion(action.processVersionId, action.user))
          case _ =>
            state.copy(status = ProblemStateStatus.Failed) //Generic error in other cases
        }
      case None =>
        StatusDetails(ProblemStateStatus.shouldBeRunning(action.processVersionId, action.user))
    }

  protected def isFinished(state: StatusDetails): Boolean =
    SimpleStateStatus.isFinishedStatus(state.status)

  // Methods below are protected in case of other state machine implementation for a given DeploymentManager
  protected def shouldAlwaysReturnStatus(state: StatusDetails): Boolean = {
    ProblemStateStatus.isProblemStatus(state.status)
  }

  protected def isFollowingDeployStatus(state: StatusDetails): Boolean = {
    SimpleStateStatus.DefaultFollowingDeployStatuses.contains(state.status)
  }

}
