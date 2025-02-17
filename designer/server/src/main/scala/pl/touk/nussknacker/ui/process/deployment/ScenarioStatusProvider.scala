package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import cats.Traverse
import cats.implicits.{toFoldableOps, toTraverseOps}
import cats.syntax.functor._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName.{Cancel, Deploy}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.inconsistency.InconsistentStateDetector
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus.FailedToGet
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.deployment.DeploymentId
import pl.touk.nussknacker.engine.util.WithDataFreshnessStatusUtils.WithDataFreshnessStatusMapOps
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.process.deployment.ScenarioStatusProvider.FragmentStateException
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.FutureUtils._
import slick.dbio.{DBIO, DBIOAction}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.control.NonFatal

trait ScenarioStatusProvider {

  def getScenariosStatuses[F[_]: Traverse, ScenarioShape](processTraverse: F[ScenarioWithDetailsEntity[ScenarioShape]])(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[F[Option[StatusDetails]]]

  def getScenarioStatus(
      processIdWithName: ProcessIdWithName,
      currentlyPresentedVersionId: Option[VersionId],
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[StatusDetails]

  def getAllowedActionsForScenarioStatus(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[StatusWithAllowedActions]

  def getAllowedActionsForScenarioStatusDBIO(processDetails: ScenarioWithDetailsEntity[_])(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): DB[StatusWithAllowedActions]

}

object ScenarioStatusProvider {

  def apply(
      dispatcher: DeploymentManagerDispatcher,
      processRepository: FetchingProcessRepository[DB],
      actionRepository: ScenarioActionRepository,
      dbioRunner: DBIOActionRunner,
      scenarioStateTimeout: Option[FiniteDuration]
  )(implicit system: ActorSystem): ScenarioStatusProvider =
    new ScenarioStatusProviderImpl(dispatcher, processRepository, actionRepository, dbioRunner, scenarioStateTimeout)

  object FragmentStateException extends BadRequestError("Fragment doesn't have state.")

}

private class ScenarioStatusProviderImpl(
    dispatcher: DeploymentManagerDispatcher,
    processRepository: FetchingProcessRepository[DB],
    actionRepository: ScenarioActionRepository,
    dbioRunner: DBIOActionRunner,
    scenarioStateTimeout: Option[FiniteDuration]
)(implicit system: ActorSystem)
    extends ScenarioStatusProvider
    with LazyLogging {

  private implicit val ec: ExecutionContext = system.dispatcher

  // TODO: check deployment id to be sure that returned status is for given deployment
  override def getScenarioStatus(
      processIdWithName: ProcessIdWithName,
      currentlyPresentedVersionId: Option[VersionId],
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[StatusDetails] = {
    dbioRunner.run(for {
      processDetailsOpt     <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id)
      processDetails        <- existsOrFail(processDetailsOpt, ProcessNotFoundError(processIdWithName.name))
      inProgressActionNames <- actionRepository.getInProgressActionNames(processDetails.processId)
      statusDetails <- getProcessStateFetchingStatusFromManager(
        processDetails,
        inProgressActionNames
      )
    } yield statusDetails)
  }

  override def getScenariosStatuses[F[_]: Traverse, ScenarioShape](
      processTraverse: F[ScenarioWithDetailsEntity[ScenarioShape]]
  )(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[F[Option[StatusDetails]]] = {
    val scenarios = processTraverse.toList
    dbioRunner.run(
      for {
        actionsInProgress <- getInProgressActionTypesForScenarios(scenarios)
        prefetchedStates  <- DBIO.from(getPrefetchedStatesForSupportedManagers(scenarios))
        statusesDetails <- processTraverse
          .map {
            case process if process.isFragment => DBIO.successful(Option.empty[StatusDetails])
            case process =>
              val prefetchedState = for {
                prefetchedStatesForProcessingType <- prefetchedStates.get(process.processingType)
                // State is prefetched for all scenarios for the given processing type.
                // If there is no information available for a specific scenario name,
                // then it means that DM is not aware of this scenario, and we should default to List.empty[StatusDetails].
                prefetchedState = prefetchedStatesForProcessingType.getOrElse(process.name, List.empty)
              } yield prefetchedState
              (prefetchedState match {
                case Some(prefetchedStatusDetails) =>
                  getProcessStateUsingPrefetchedStatus(
                    process,
                    actionsInProgress.getOrElse(process.processId, Set.empty),
                    prefetchedStatusDetails,
                  )
                case None =>
                  getProcessStateFetchingStatusFromManager(
                    process,
                    actionsInProgress.getOrElse(process.processId, Set.empty),
                  )
              }).map(Some(_))
          }
          .sequence[DB, Option[StatusDetails]]
      } yield statusesDetails
    )
  }

  override def getAllowedActionsForScenarioStatus(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[StatusWithAllowedActions] = {
    dbioRunner.run(getAllowedActionsForScenarioStatusDBIO(processDetails))
  }

  override def getAllowedActionsForScenarioStatusDBIO(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): DB[StatusWithAllowedActions] = {
    for {
      inProgressActionNames <- actionRepository.getInProgressActionNames(processDetails.processId)
      statusDetails <- getProcessStateFetchingStatusFromManager(
        processDetails,
        inProgressActionNames
      )
      allowedActions = getAllowedActions(statusDetails, processDetails, None)
    } yield StatusWithAllowedActions(statusDetails, allowedActions)
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
          statusDetails = statusDetails,
          latestVersionId = processDetails.processVersionId,
          deployedVersionId = processDetails.lastDeployedAction.map(_.processVersionId),
          currentlyPresentedVersionId = currentlyPresentedVersionId
        )
      )
  }

  private def getProcessStateFetchingStatusFromManager(
      processDetails: ScenarioWithDetailsEntity[_],
      inProgressActionNames: Set[ScenarioActionName],
  )(implicit freshnessPolicy: DataFreshnessPolicy, user: LoggedUser): DB[StatusDetails] = {
    getScenarioStatusDetails(
      processDetails,
      inProgressActionNames,
      manager =>
        getStateFromDeploymentManager(
          manager,
          processDetails.idWithName,
          processDetails.lastStateAction,
        )
    )
  }

  // DeploymentManager's may support fetching state of all scenarios at once
  // State is prefetched only when:
  //  - DM has capability StateQueryForAllScenariosSupported
  //  - the query is about more than one scenario handled by that DM
  private def getPrefetchedStatesForSupportedManagers(
      scenarios: List[ScenarioWithDetailsEntity[_]],
  )(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[Map[ProcessingType, WithDataFreshnessStatus[Map[ProcessName, List[StatusDetails]]]]] = {
    val allProcessingTypes = scenarios.map(_.processingType).toSet
    val numberOfScenariosByProcessingType =
      allProcessingTypes
        .map(processingType => (processingType, scenarios.count(_.processingType == processingType)))
        .toMap
    val processingTypesWithMoreThanOneScenario = numberOfScenariosByProcessingType.filter(_._2 > 1).keys

    Future
      .sequence {
        processingTypesWithMoreThanOneScenario.map { processingType =>
          (for {
            manager <- dispatcher.deploymentManager(processingType)
            managerWithCapability <- manager.stateQueryForAllScenariosSupport match {
              case supported: StateQueryForAllScenariosSupported => Some(supported)
              case NoStateQueryForAllScenariosSupport            => None
            }
          } yield getAllProcessesStates(processingType, managerWithCapability))
            .getOrElse(Future.successful(None))
        }
      }
      .map(_.flatten.toMap)
  }

  private def getAllProcessesStates(processingType: ProcessingType, manager: StateQueryForAllScenariosSupported)(
      implicit freshnessPolicy: DataFreshnessPolicy,
  ): Future[Option[(ProcessingType, WithDataFreshnessStatus[Map[ProcessName, List[StatusDetails]]])]] = {
    manager
      .getAllProcessesStates()
      .map(states => Some((processingType, states)))
      .recover { case NonFatal(e) =>
        logger.warn(
          s"Failed to get statuses of all scenarios in deployment manager for $processingType: ${e.getMessage}",
          e
        )
        None
      }
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

  private def getProcessStateUsingPrefetchedStatus(
      processDetails: ScenarioWithDetailsEntity[_],
      inProgressActionNames: Set[ScenarioActionName],
      prefetchedStatusDetails: WithDataFreshnessStatus[List[StatusDetails]],
  )(implicit user: LoggedUser): DB[StatusDetails] = {
    getScenarioStatusDetails(
      processDetails,
      inProgressActionNames,
      { _ =>
        // FIXME abr: handle finished, it has no sense for periodic but it shouldn't hurt us
        Future {
          prefetchedStatusDetails.map { prefetchedStatusDetailsValue =>
            // FIXME abr: resolved states shouldn't be handled here
            InconsistentStateDetector.resolve(prefetchedStatusDetailsValue, processDetails.lastStateAction)
          }
        }
      }
    )
  }

  private def getScenarioStatusDetails(
      processDetails: ScenarioWithDetailsEntity[_],
      inProgressActionNames: Set[ScenarioActionName],
      fetchState: DeploymentManager => Future[WithDataFreshnessStatus[StatusDetails]],
  )(implicit user: LoggedUser): DB[StatusDetails] = {
    dispatcher
      .deploymentManager(processDetails.processingType)
      .map { manager =>
        if (processDetails.isFragment) {
          throw FragmentStateException
        } else if (processDetails.isArchived) {
          DBIOAction.successful(getArchivedProcessState(processDetails))
        } else if (inProgressActionNames.contains(ScenarioActionName.Deploy)) {
          logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.DuringDeploy}")
          DBIOAction.successful(
            StatusDetails(SimpleStateStatus.DuringDeploy, None)
          ) // FIXME abr: deploymentId from inProgressActionNames
        } else if (inProgressActionNames.contains(ScenarioActionName.Cancel)) {
          logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.DuringCancel}")
          DBIOAction.successful(StatusDetails(SimpleStateStatus.DuringCancel, None))
        } else {
          processDetails.lastStateAction match {
            case Some(_) =>
              DBIOAction
                .from(fetchState(manager))
                .map { statusWithFreshness =>
                  logger.debug(
                    s"Status for: '${processDetails.name}' is: ${statusWithFreshness.value.status}, cached: ${statusWithFreshness.cached}, last status action: ${processDetails.lastStateAction
                        .map(_.actionName)})"
                  )
                  statusWithFreshness.value
                }
            case _ => // We assume that the process never deployed should have no state at the engine
              // FIXME abr: it is a part of deployment => scenario status resolution
              logger.debug(s"Status for never deployed: '${processDetails.name}' is: ${SimpleStateStatus.NotDeployed}")
              DBIOAction.successful(StatusDetails(SimpleStateStatus.NotDeployed, None))
          }
        }
      }
      // FIXME abr: it is a part of deployment => scenario status resolution
      .getOrElse(
        DBIOAction.successful(StatusDetails(FailedToGet, None))
      )
  }

  // We assume that checking the state for archived doesn't make sense, and we compute the state based on the last state action
  private def getArchivedProcessState(processDetails: ScenarioWithDetailsEntity[_]): StatusDetails = {
    processDetails.lastStateAction.map(a => (a.actionName, a.state, a.id)) match {
      case Some((Cancel, _, _)) =>
        logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.Canceled}")
        StatusDetails(SimpleStateStatus.Canceled, None)
      case Some((Deploy, ProcessActionState.ExecutionFinished, deploymentActionId)) =>
        logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.Finished} ")
        StatusDetails(SimpleStateStatus.Finished, Some(DeploymentId.fromActionId(deploymentActionId)))
      case Some(_) =>
        logger.warn(s"Status for: '${processDetails.name}' is: ${ProblemStateStatus.ArchivedShouldBeCanceled}")
        // FIXME abr: it is a part of deployment => scenario status resolution
        StatusDetails(ProblemStateStatus.ArchivedShouldBeCanceled, None)
      case None =>
        logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.NotDeployed}")
        // FIXME abr: it is a part of deployment => scenario status resolution
        StatusDetails(SimpleStateStatus.NotDeployed, None)
    }
  }

  private def getStateFromDeploymentManager(
      deploymentManager: DeploymentManager,
      processIdWithName: ProcessIdWithName,
      lastStateAction: Option[ProcessAction]
  )(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[StatusDetails]] = {

    // FIXME abr: handle finished, it has no sense for periodic but it shouldn't hurt us
    val state = deploymentManager
      .getScenarioDeploymentsStatuses(processIdWithName.name)
      .map(_.map { statusDetails =>
        // FIXME abr: resolved states shouldn't be handled here
        InconsistentStateDetector.resolve(statusDetails, lastStateAction)
      })
      .recover { case NonFatal(e) =>
        logger.warn(s"Failed to get status of ${processIdWithName.name}: ${e.getMessage}", e)
        failedToGetProcessState
      }

    scenarioStateTimeout
      .map { timeout =>
        state.withTimeout(timeout, timeoutResult = failedToGetProcessState).map {
          case CompletedNormally(value) =>
            value
          case CompletedByTimeout(value) =>
            logger
              .warn(s"Timeout: $timeout occurred during waiting for response from engine for ${processIdWithName.name}")
            value
        }
      }
      .getOrElse(state)
  }

  private val failedToGetProcessState =
    WithDataFreshnessStatus.fresh(StatusDetails(FailedToGet, None))

  private def existsOrFail[T](checkThisOpt: Option[T], failWith: => Exception): DB[T] = {
    checkThisOpt match {
      case Some(checked) => DBIOAction.successful(checked)
      case None          => DBIOAction.failed(failWith)
    }
  }

}

final case class StatusWithAllowedActions(statusDetails: StatusDetails, allowedActions: Set[ScenarioActionName]) {

  def status: StateStatus = statusDetails.status

}
