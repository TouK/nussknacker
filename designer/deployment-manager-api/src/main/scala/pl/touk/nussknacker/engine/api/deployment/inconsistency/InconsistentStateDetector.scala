package pl.touk.nussknacker.engine.api.deployment.inconsistency

import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessState, StatusDetails}

object InconsistentStateDetector {

  //This method handles some corner cases like retention for keeping old states - some engine can cleanup canceled states. It's more Flink hermetic.
  def resolve(statusDetails: Option[StatusDetails], lastStateAction: Option[ProcessAction]): StatusDetails =
    (statusDetails, lastStateAction) match {
      case (Some(state), _) if state.status.isFailed => state
      case (Some(state), _) if state.status == SimpleStateStatus.Restarting => handleRestartingState(state, lastStateAction)
      case (_, Some(action)) if action.isDeployed => handleMismatchDeployedStateLastAction(statusDetails, action)
      case (Some(state), _) if state.status.isRunning || state.status.isDuringDeploy => handleFollowingDeployState(state, lastStateAction)
      case (_, Some(action)) if action.isCanceled => handleCanceledState(statusDetails)
      case (Some(state), _) => handleState(state, lastStateAction)
      case (None, Some(_)) => StatusDetails(SimpleStateStatus.NotDeployed)
      case (None, None) => StatusDetails(SimpleStateStatus.NotDeployed)
    }

  private def handleState(state: StatusDetails, lastStateAction: Option[ProcessAction]): StatusDetails =
    state.status match {
      case SimpleStateStatus.NotDeployed if lastStateAction.isEmpty =>
        state.copy(status = SimpleStateStatus.NotDeployed)
      case SimpleStateStatus.Restarting | SimpleStateStatus.DuringCancel | SimpleStateStatus.Finished if lastStateAction.isEmpty =>
        state.copy(status = ProblemStateStatus.ProcessWithoutAction)
      case _ => state
    }

  //Thise method handles some corner cases for canceled process -> with last action = Canceled
  private def handleCanceledState(processState: Option[StatusDetails]): StatusDetails =
    processState match {
      case Some(state) => state.status match {
        case _ => state
      }
      case None => StatusDetails(SimpleStateStatus.Canceled)
    }

  private def handleRestartingState(state: StatusDetails, lastStateAction: Option[ProcessAction]): StatusDetails =
    lastStateAction match {
      case Some(action) if action.isDeployed => state
      case _ => handleState(state, lastStateAction)
    }

  //This method handles some corner cases for following deploy state mismatch last action version
  private def handleFollowingDeployState(state: StatusDetails, lastStateAction: Option[ProcessAction]): StatusDetails =
    lastStateAction match {
      case Some(action) if !action.isDeployed =>
        state.copy(status = ProblemStateStatus.shouldNotBeRunning(true))
      case Some(_) =>
        state
      case None =>
        state.copy(status = ProblemStateStatus.shouldNotBeRunning(false))
    }

  //This method handles some corner cases for deployed action mismatch state version
  private def handleMismatchDeployedStateLastAction(processState: Option[StatusDetails], action: ProcessAction): StatusDetails =
    processState match {
      case Some(state) =>
        state.version match {
          case _ if !(state.status.isRunning || state.status.isDuringDeploy) =>
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


}
