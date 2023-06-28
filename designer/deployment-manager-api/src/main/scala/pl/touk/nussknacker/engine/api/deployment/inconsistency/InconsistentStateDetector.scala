package pl.touk.nussknacker.engine.api.deployment.inconsistency

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessState, StatusDetails}

object InconsistentStateDetector extends InconsistentStateDetector

class InconsistentStateDetector extends LazyLogging {

  //This method handles some corner cases like retention for keeping old states - some engine can cleanup canceled states. It's more Flink hermetic.
  def resolve(statusDetails: Option[StatusDetails], lastStateAction: Option[ProcessAction]): StatusDetails = {
    val status = (statusDetails, lastStateAction) match {
      case (Some(state), _) if shouldAlwaysReturnStatus(state) => state
      case (Some(state), _) if state.status == SimpleStateStatus.Restarting => handleRestartingState(state, lastStateAction)
      case (_, Some(action)) if action.isDeployed => handleMismatchDeployedStateLastAction(statusDetails, action)
      case (Some(state), _) if isFollowingDeployStatus(state) => handleFollowingDeployState(state, lastStateAction)
      case (_, Some(action)) if action.isCanceled => handleCanceledState(statusDetails)
      case (Some(state), _) => handleState(state, lastStateAction)
      case (None, Some(_)) => StatusDetails(SimpleStateStatus.NotDeployed)
      case (None, None) => StatusDetails(SimpleStateStatus.NotDeployed)
    }
    logger.debug(s"Resolved $statusDetails , lastStateAction: $lastStateAction to status $status")
    status
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
            logger.debug(s"handleMismatchDeployedStateLastAction: is not following deploy status, but it should be. $state")
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
        logger.debug(s"handleMismatchDeployedStateLastAction for empty statusDetails. Action.processVersionId: ${action.processVersionId}")
        StatusDetails(ProblemStateStatus.shouldBeRunning(action.processVersionId, action.user))
    }

  // Methods below are protected in case of other state machine implementation for a given DeploymentManager
  protected def shouldAlwaysReturnStatus(state: StatusDetails): Boolean = {
    ProblemStateStatus.isProblemStatus(state.status)
  }

  protected def isFollowingDeployStatus(state: StatusDetails): Boolean = {
    SimpleStateStatus.DefaultFollowingDeployStatuses.contains(state.status)
  }
}
