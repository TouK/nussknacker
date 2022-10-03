package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.restmodel.processdetails.ProcessAction

object ObsoleteStateDetector {

  //This method handles some corner cases like retention for keeping old states - some engine can cleanup canceled states. It's more Flink hermetic.
  //TODO: In future we should move this functionality to DeploymentManager.
  def handleObsoleteStatus(processState: Option[ProcessState], lastAction: Option[ProcessAction]): ProcessState =
    (processState, lastAction) match {
      case (Some(state), _) if state.status.isFailed => state
      case (Some(state), _) if state.status == SimpleStateStatus.Restarting => handleRestartingState(state, lastAction)
      case (_, Some(action)) if action.isDeployed => handleMismatchDeployedLastAction(processState, action)
      case (Some(state), _) if state.isDeployed => handleFollowingDeployState(state, lastAction)
      case (_, Some(action)) if action.isCanceled => handleCanceledState(processState)
      case (Some(state), _) => handleState(state, lastAction)
      case (None, Some(_)) => SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.NotDeployed)
      case (None, None) => SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.NotDeployed)
    }

  //TODO: In future we should move this functionality to DeploymentManager.
  private def handleState(state: ProcessState, lastAction: Option[ProcessAction]): ProcessState =
    state.status match {
      case SimpleStateStatus.NotDeployed if lastAction.isEmpty =>
        SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.NotDeployed)
      case SimpleStateStatus.Restarting | SimpleStateStatus.DuringCancel | SimpleStateStatus.Finished if lastAction.isEmpty =>
        state.withStatusDetails(SimpleProcessStateDefinitionManager.warningProcessWithoutActionState)
      case _ => state
    }

  //Thise method handles some corner cases for canceled process -> with last action = Canceled
  //TODO: In future we should move this functionality to DeploymentManager.
  private def handleCanceledState(processState: Option[ProcessState]): ProcessState =
    processState match {
      case Some(state) => state.status match {
        case _ => state
      }
      case None => SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.Canceled)
    }

  private def handleRestartingState(state: ProcessState, lastAction: Option[ProcessAction]): ProcessState =
    lastAction match {
      case Some(action) if action.isDeployed => state
      case _ => handleState(state, lastAction)
    }

  //This method handles some corner cases for following deploy state mismatch last action version
  //TODO: In future we should move this functionality to DeploymentManager.
  private def handleFollowingDeployState(state: ProcessState, lastAction: Option[ProcessAction]): ProcessState =
    lastAction match {
      case Some(action) if !action.isDeployed =>
        state.withStatusDetails(SimpleProcessStateDefinitionManager.warningShouldNotBeRunningState(true))
      case Some(_) =>
        state
      case None =>
        state.withStatusDetails(SimpleProcessStateDefinitionManager.warningShouldNotBeRunningState(false))
    }

  //This method handles some corner cases for deployed action mismatch state version
  //TODO: In future we should move this functionality to DeploymentManager.
  private def handleMismatchDeployedLastAction(processState: Option[ProcessState], action: ProcessAction): ProcessState =
    processState match {
      case Some(state) =>
        state.version match {
          case _ if !state.isDeployed =>
            state.withStatusDetails(SimpleProcessStateDefinitionManager.errorShouldBeRunningState(action.processVersionId, action.user))
          case Some(ver) if ver.versionId != action.processVersionId =>
            state.withStatusDetails(SimpleProcessStateDefinitionManager.errorMismatchDeployedVersionState(ver.versionId, action.processVersionId, action.user))
          case Some(ver) if ver.versionId == action.processVersionId =>
            state
          case None => //TODO: we should remove Option from ProcessVersion?
            state.withStatusDetails(SimpleProcessStateDefinitionManager.warningMissingDeployedVersionState(action.processVersionId, action.user))
          case _ =>
            SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.Error) //Generic error in other cases
        }
      case None =>
        SimpleProcessStateDefinitionManager.errorShouldBeRunningState(action.processVersionId, action.user)
    }


}
