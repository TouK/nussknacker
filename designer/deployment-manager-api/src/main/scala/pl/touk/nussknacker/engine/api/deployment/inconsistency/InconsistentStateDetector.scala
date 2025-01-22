package pl.touk.nussknacker.engine.api.deployment.inconsistency

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionState, ScenarioActionName, StatusDetails}
import pl.touk.nussknacker.engine.deployment.DeploymentId

object InconsistentStateDetector extends InconsistentStateDetector

class InconsistentStateDetector extends LazyLogging {

  def resolve(statusDetails: List[StatusDetails], lastStateAction: Option[ProcessAction]): StatusDetails = {
    val status = (doExtractAtMostOneStatus(statusDetails), lastStateAction) match {
      case (Left(state), _)                                           => state
      case (Right(Some(state)), _) if shouldAlwaysReturnStatus(state) => state
      case (Right(Some(state)), _) if state.status == SimpleStateStatus.Restarting =>
        handleRestartingState(state, lastStateAction)
      case (Right(statusDetailsOpt), Some(action))
          if action.actionName == ScenarioActionName.Deploy && action.state == ProcessActionState.ExecutionFinished =>
        handleLastActionFinishedDeploy(statusDetailsOpt, action)
      case (Right(statusDetailsOpt), Some(action)) if action.actionName == ScenarioActionName.Deploy =>
        handleLastActionDeploy(statusDetailsOpt, action)
      case (Right(Some(state)), _) if isFollowingDeployStatus(state) =>
        handleFollowingDeployState(state, lastStateAction)
      case (Right(statusDetailsOpt), Some(action)) if action.actionName == ScenarioActionName.Cancel =>
        handleCanceledState(statusDetailsOpt)
      case (Right(Some(state)), _) => handleState(state, lastStateAction)
      case (Right(None), Some(a)) => StatusDetails(SimpleStateStatus.NotDeployed, Some(DeploymentId.fromActionId(a.id)))
      case (Right(None), None)    => StatusDetails(SimpleStateStatus.NotDeployed, None)
    }
    logger.debug(s"Resolved $statusDetails , lastStateAction: $lastStateAction to status $status")
    status
  }

  // TODO: This method is exposed to make transition between Option[StatusDetails] and List[StatusDetails] easier to perform.
  //       After full migration to List[StatusDetails], this method should be removed
  def extractAtMostOneStatus(statusDetails: List[StatusDetails]): Option[StatusDetails] =
    doExtractAtMostOneStatus(statusDetails).fold(Some(_), identity)

  private def doExtractAtMostOneStatus(
      statusDetails: List[StatusDetails]
  ): Either[StatusDetails, Option[StatusDetails]] = {
    val notFinalStatuses = statusDetails.filterNot(isFinalOrTransitioningToFinalStatus)
    (statusDetails, notFinalStatuses) match {
      case (Nil, Nil)                    => Right(None)
      case (_, singleNotFinished :: Nil) => Right(Some(singleNotFinished))
      case (_, firstNotFinished :: _ :: _) =>
        Left(
          firstNotFinished.copy(
            status = ProblemStateStatus.MultipleJobsRunning,
            errors = List(s"Expected one job, instead: ${notFinalStatuses
                .map(details => details.externalDeploymentId.map(_.value).getOrElse("missing") + " - " + details.status)
                .mkString(", ")}")
          )
        )
      case (firstFinished :: _, Nil) => Right(Some(firstFinished))
    }
  }

  private def handleState(statusDetails: StatusDetails, lastStateAction: Option[ProcessAction]): StatusDetails =
    statusDetails.status match {
      case SimpleStateStatus.Restarting | SimpleStateStatus.DuringCancel | SimpleStateStatus.Finished
          if lastStateAction.isEmpty =>
        statusDetails.copy(status = ProblemStateStatus.ProcessWithoutAction)
      case _ => statusDetails
    }

  // This method handles some corner cases for canceled process -> with last action = Canceled
  private def handleCanceledState(statusDetailsOpt: Option[StatusDetails]): StatusDetails =
    statusDetailsOpt
      // Missing deployment is fine for cancelled action as well because of retention of states
      .getOrElse(StatusDetails(SimpleStateStatus.Canceled, None))

  private def handleRestartingState(
      statusDetails: StatusDetails,
      lastStateAction: Option[ProcessAction]
  ): StatusDetails =
    lastStateAction match {
      case Some(action) if action.actionName == ScenarioActionName.Deploy => statusDetails
      case _                                                              => handleState(statusDetails, lastStateAction)
    }

  // This method handles some corner cases for following deploy state mismatch last action version
  private def handleFollowingDeployState(
      statusDetails: StatusDetails,
      lastStateAction: Option[ProcessAction]
  ): StatusDetails =
    lastStateAction match {
      case Some(action) if action.actionName != ScenarioActionName.Deploy =>
        statusDetails.copy(status = ProblemStateStatus.shouldNotBeRunning(true))
      case Some(_) =>
        statusDetails
      case None =>
        statusDetails.copy(status = ProblemStateStatus.shouldNotBeRunning(false))
    }

  private def handleLastActionFinishedDeploy(
      statusDetailsOpt: Option[StatusDetails],
      action: ProcessAction
  ): StatusDetails =
    statusDetailsOpt match {
      case Some(state) =>
        state
      case None =>
        // Some engines like Flink have jobs retention. Because of that we restore finished state. See FlinkDeploymentManager.postprocess
        StatusDetails(SimpleStateStatus.Finished, Some(DeploymentId.fromActionId(action.id)))
    }

  // This method handles some corner cases for deployed action mismatch state version
  private def handleLastActionDeploy(statusDetailsOpt: Option[StatusDetails], action: ProcessAction): StatusDetails =
    statusDetailsOpt match {
      case Some(state) =>
        state.version match {
          case _ if !isFollowingDeployStatus(state) && !isFinishedStatus(state) =>
            logger.debug(
              s"handleLastActionDeploy: is not following deploy status nor finished, but it should be. $state"
            )
            state.copy(status = ProblemStateStatus.shouldBeRunning(action.processVersionId, action.user))
          case Some(ver) if ver.versionId != action.processVersionId =>
            state.copy(status =
              ProblemStateStatus.mismatchDeployedVersion(ver.versionId, action.processVersionId, action.user)
            )
          case Some(ver) if ver.versionId == action.processVersionId =>
            state
          case None => // TODO: we should remove Option from ProcessVersion?
            state.copy(status = ProblemStateStatus.missingDeployedVersion(action.processVersionId, action.user))
          case _ =>
            state.copy(status = ProblemStateStatus.Failed) // Generic error in other cases
        }
      case None =>
        logger.debug(
          s"handleLastActionDeploy for empty statusDetails. Action.processVersionId: ${action.processVersionId}"
        )
        StatusDetails(ProblemStateStatus.shouldBeRunning(action.processVersionId, action.user), None)
    }

  // Methods below are protected in case of other state machine implementation for a given DeploymentManager
  protected def shouldAlwaysReturnStatus(state: StatusDetails): Boolean = {
    ProblemStateStatus.isProblemStatus(state.status)
  }

  protected def isFollowingDeployStatus(state: StatusDetails): Boolean = {
    SimpleStateStatus.DefaultFollowingDeployStatuses.contains(state.status)
  }

  protected def isFinalOrTransitioningToFinalStatus(state: StatusDetails): Boolean =
    SimpleStateStatus.isFinalOrTransitioningToFinalStatus(state.status)

  protected def isFinishedStatus(state: StatusDetails): Boolean = {
    state.status == SimpleStateStatus.Finished
  }

}
