package pl.touk.nussknacker.ui.process.deployment.scenariostatus

import cats.Traverse
import cats.implicits.{toFoldableOps, toTraverseOps}
import cats.syntax.functor._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName.{Cancel, Deploy}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus.FailedToGet
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.deployment.DeploymentId
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.{
  DeploymentStatusesProvider,
  GetDeploymentsStatusesError,
  PrefetchedDeploymentStatuses
}
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.dbio.{DBIO, DBIOAction}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

class ScenarioStatusProvider(
    deploymentStatusesProvider: DeploymentStatusesProvider,
    dispatcher: DeploymentManagerDispatcher,
    processRepository: FetchingProcessRepository[DB],
    actionRepository: ScenarioActionRepository,
    dbioRunner: DBIOActionRunner,
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  def getScenarioStatus(
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[StatusDetails] = {
    dbioRunner.run(for {
      processDetailsOpt     <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id)
      processDetails        <- existsOrFail(processDetailsOpt, ProcessNotFoundError(processIdWithName.name))
      inProgressActionNames <- actionRepository.getInProgressActionNames(processDetails.processId)
      scenarioStatus <- getScenarioStatusFetchingDeploymentsStatusesFromManager(
        processDetails,
        inProgressActionNames
      )
    } yield scenarioStatus)
  }

  def getScenariosStatuses[F[_]: Traverse, ScenarioShape](
      processTraverse: F[ScenarioWithDetailsEntity[ScenarioShape]]
  )(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[F[Option[StatusDetails]]] = {
    val scenarios = processTraverse.toList
    dbioRunner.run(
      for {
        actionsInProgress <- getInProgressActionTypesForScenarios(scenarios)
        prefetchedDeploymentStatuses <- DBIO.from(
          deploymentStatusesProvider.getPrefetchedDeploymentStatusesForSupportedManagers(scenarios)
        )
        finalScenariosStatuses <- processTraverse
          .map {
            case process if process.isFragment => DBIO.successful(Option.empty[StatusDetails])
            case process =>
              getNonFragmentScenarioStatus(actionsInProgress, prefetchedDeploymentStatuses, process).map(Some(_))
          }
          .sequence[DB, Option[StatusDetails]]
      } yield finalScenariosStatuses
    )
  }

  private def getNonFragmentScenarioStatus[ScenarioShape, F[_]: Traverse](
      actionsInProgress: Map[ProcessId, Set[ScenarioActionName]],
      prefetchedDeploymentStatuses: PrefetchedDeploymentStatuses,
      process: ScenarioWithDetailsEntity[ScenarioShape]
  )(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): DB[StatusDetails] = {
    val inProgressActionNames = actionsInProgress.getOrElse(process.processId, Set.empty)
    getScenarioStatusDetails(
      process,
      inProgressActionNames,
      deploymentStatusesProvider.getDeploymentStatuses(
        process.processingType,
        process.name,
        Some(prefetchedDeploymentStatuses)
      )
    )
  }

  def getAllowedActionsForScenarioStatus(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[ScenarioStatusWithAllowedActions] = {
    dbioRunner.run(getAllowedActionsForScenarioStatusDBIO(processDetails))
  }

  def getAllowedActionsForScenarioStatusDBIO(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): DB[ScenarioStatusWithAllowedActions] = {
    for {
      inProgressActionNames <- actionRepository.getInProgressActionNames(processDetails.processId)
      statusDetails <- getScenarioStatusFetchingDeploymentsStatusesFromManager(
        processDetails,
        inProgressActionNames
      )
      allowedActions = getAllowedActions(statusDetails, processDetails, None)
    } yield ScenarioStatusWithAllowedActions(statusDetails, allowedActions)
  }

  private def getAllowedActions(
      statusDetails: StatusDetails,
      processDetails: ScenarioWithDetailsEntity[_],
      currentlyPresentedVersionId: Option[VersionId]
  )(implicit user: LoggedUser): Set[ScenarioActionName] = {
    dispatcher
      .deploymentManagerUnsafe(processDetails.processingType)
      .processStateDefinitionManager
      .statusActions(
        ScenarioStatusWithScenarioContext(
          scenarioStatusDetails = statusDetails,
          latestVersionId = processDetails.processVersionId,
          deployedVersionId = processDetails.lastDeployedAction.map(_.processVersionId),
          currentlyPresentedVersionId = currentlyPresentedVersionId
        )
      )
  }

  private def getScenarioStatusFetchingDeploymentsStatusesFromManager(
      processDetails: ScenarioWithDetailsEntity[_],
      inProgressActionNames: Set[ScenarioActionName],
  )(implicit freshnessPolicy: DataFreshnessPolicy, user: LoggedUser): DB[StatusDetails] = {
    getScenarioStatusDetails(
      processDetails,
      inProgressActionNames,
      deploymentStatusesProvider.getDeploymentStatuses(
        processDetails.processingType,
        processDetails.name,
        prefetchedDeploymentStatuses = None
      )
    )
  }

  // This is optimisation tweak. We want to reduce number of calls for in progress action types. So for >1 scenarios
  // we do one call for all in progress action types for all scenarios
  private def getInProgressActionTypesForScenarios(
      scenarios: List[ScenarioWithDetailsEntity[_]]
  ): DB[Map[ProcessId, Set[ScenarioActionName]]] = {
    scenarios match {
      case Nil => DBIO.successful(Map.empty)
      case head :: Nil =>
        actionRepository
          .getInProgressActionNames(head.processId)
          .map(actionNames => Map(head.processId -> actionNames))
      case _ =>
        // We are getting only Deploy and Cancel InProgress actions as only these two impact scenario status
        actionRepository.getInProgressActionNames(Set(Deploy, Cancel))
    }
  }

  private def getScenarioStatusDetails(
      processDetails: ScenarioWithDetailsEntity[_],
      inProgressActionNames: Set[ScenarioActionName],
      fetchDeploymentStatuses: => Future[
        Either[GetDeploymentsStatusesError, WithDataFreshnessStatus[List[StatusDetails]]]
      ],
  ): DB[StatusDetails] = {
    if (processDetails.isFragment) {
      throw FragmentStateException
    } else if (processDetails.isArchived) {
      DBIOAction.successful(getArchivedScenarioStatus(processDetails))
    } else if (inProgressActionNames.contains(ScenarioActionName.Deploy)) {
      logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.DuringDeploy}")
      DBIOAction.successful(
        StatusDetails(SimpleStateStatus.DuringDeploy, None)
      )
    } else if (inProgressActionNames.contains(ScenarioActionName.Cancel)) {
      logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.DuringCancel}")
      DBIOAction.successful(StatusDetails(SimpleStateStatus.DuringCancel, None))
    } else {
      processDetails.lastStateAction match {
        case Some(lastStateActionValue) =>
          DBIOAction
            .from(fetchDeploymentStatuses)
            .map {
              case Left(error) =>
                logger.warn("Failure during getting deployment statuses from deployment manager", error)
                StatusDetails(FailedToGet, None)
              case Right(statusWithFreshness) =>
                logger.debug(
                  s"Deployment statuses for: '${processDetails.name}' are: ${statusWithFreshness.value}, cached: ${statusWithFreshness.cached}, last status action: ${processDetails.lastStateAction
                      .map(_.actionName)})"
                )
                InconsistentStateDetector.resolveScenarioStatus(statusWithFreshness.value, lastStateActionValue)
            }
        case None => // We assume that the process never deployed should have no state at the engine
          logger.debug(s"Status for never deployed: '${processDetails.name}' is: ${SimpleStateStatus.NotDeployed}")
          DBIOAction.successful(StatusDetails(SimpleStateStatus.NotDeployed, None))
      }
    }
  }

  // We assume that checking the state for archived doesn't make sense, and we compute the state based on the last state action
  private def getArchivedScenarioStatus(processDetails: ScenarioWithDetailsEntity[_]): StatusDetails = {
    processDetails.lastStateAction.map(a => (a.actionName, a.state, a.id)) match {
      case Some((Cancel, _, _)) =>
        logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.Canceled}")
        StatusDetails(SimpleStateStatus.Canceled, None)
      case Some((Deploy, ProcessActionState.ExecutionFinished, deploymentActionId)) =>
        logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.Finished} ")
        StatusDetails(SimpleStateStatus.Finished, Some(DeploymentId.fromActionId(deploymentActionId)))
      case Some(_) =>
        logger.warn(s"Status for: '${processDetails.name}' is: ${ProblemStateStatus.ArchivedShouldBeCanceled}")
        StatusDetails(ProblemStateStatus.ArchivedShouldBeCanceled, None)
      case None =>
        logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.NotDeployed}")
        StatusDetails(SimpleStateStatus.NotDeployed, None)
    }
  }

  private def existsOrFail[T](checkThisOpt: Option[T], failWith: => Exception): DB[T] = {
    checkThisOpt match {
      case Some(checked) => DBIOAction.successful(checked)
      case None          => DBIOAction.failed(failWith)
    }
  }

}

final case class ScenarioStatusWithAllowedActions(
    scenarioStatusDetails: StatusDetails,
    allowedActions: Set[ScenarioActionName]
) {

  def scenarioStatus: StateStatus = scenarioStatusDetails.status

}

object FragmentStateException extends BadRequestError("Fragment doesn't have state.")
