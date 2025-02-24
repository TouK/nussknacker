package pl.touk.nussknacker.ui.process.deployment

import org.apache.pekko.actor.ActorSystem
import cats.Traverse
import cats.implicits.{toFoldableOps, toTraverseOps}
import cats.syntax.functor._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName.{Cancel, Deploy}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.util.WithDataFreshnessStatusUtils.{
  WithDataFreshnessStatusMapOps,
  WithDataFreshnessStatusOps
}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.process.ScenarioWithDetailsConversions.Ops
import pl.touk.nussknacker.ui.process.deployment.ScenarioStateProvider.FragmentStateException
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.FutureUtils._
import slick.dbio.{DBIO, DBIOAction}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.control.NonFatal

trait ScenarioStateProvider {

  def enrichDetailsWithProcessState[F[_]: Traverse](processTraverse: F[ScenarioWithDetails])(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[F[ScenarioWithDetails]]

  def getProcessState(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState]

  def getProcessState(
      processIdWithName: ProcessIdWithName,
      currentlyPresentedVersionId: Option[VersionId],
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState]

  def getProcessStateDBIO(processDetails: ScenarioWithDetailsEntity[_], currentlyPresentedVersionId: Option[VersionId])(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): DB[ProcessState]

}

object ScenarioStateProvider {

  def apply(
      dispatcher: DeploymentManagerDispatcher,
      processRepository: FetchingProcessRepository[DB],
      actionRepository: ScenarioActionRepository,
      dbioRunner: DBIOActionRunner,
      scenarioStateTimeout: Option[FiniteDuration]
  )(implicit system: ActorSystem): ScenarioStateProvider =
    new ScenarioStateProviderImpl(dispatcher, processRepository, actionRepository, dbioRunner, scenarioStateTimeout)

  object FragmentStateException extends BadRequestError("Fragment doesn't have state.")

}

private class ScenarioStateProviderImpl(
    dispatcher: DeploymentManagerDispatcher,
    processRepository: FetchingProcessRepository[DB],
    actionRepository: ScenarioActionRepository,
    dbioRunner: DBIOActionRunner,
    scenarioStateTimeout: Option[FiniteDuration]
)(implicit system: ActorSystem)
    extends ScenarioStateProvider
    with LazyLogging {

  private implicit val ec: ExecutionContext = system.dispatcher

  // TODO: check deployment id to be sure that returned status is for given deployment
  def getProcessState(
      processIdWithName: ProcessIdWithName,
      currentlyPresentedVersionId: Option[VersionId],
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState] = {
    dbioRunner.run(for {
      processDetailsOpt <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id)
      processDetails    <- existsOrFail(processDetailsOpt, ProcessNotFoundError(processIdWithName.name))
      result <- getProcessStateDBIO(
        processDetails,
        currentlyPresentedVersionId
      )
    } yield result)
  }

  def getProcessStateDBIO(
      processDetails: ScenarioWithDetailsEntity[_],
      currentlyPresentedVersionId: Option[VersionId]
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): DB[ProcessState] = {
    for {
      inProgressActionNames <- actionRepository.getInProgressActionNames(processDetails.processId)
      result <- getProcessStateFetchingStatusFromManager(
        processDetails,
        inProgressActionNames,
        currentlyPresentedVersionId
      )
    } yield result
  }

  def getProcessState(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState] = {
    dbioRunner.run(for {
      inProgressActionNames <- actionRepository.getInProgressActionNames(processDetails.processId)
      result                <- getProcessStateFetchingStatusFromManager(processDetails, inProgressActionNames, None)
    } yield result)
  }

  def enrichDetailsWithProcessState[F[_]: Traverse](processTraverse: F[ScenarioWithDetails])(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[F[ScenarioWithDetails]] = {
    val scenarios = processTraverse.toList
    dbioRunner.run(
      for {
        actionsInProgress <- getInProgressActionTypesForScenarios(scenarios)
        prefetchedStates  <- DBIO.from(getPrefetchedStatesForSupportedManagers(scenarios))
        processesWithState <- processTraverse
          .map {
            case process if process.isFragment => DBIO.successful(process)
            case process =>
              val prefetchedState = for {
                prefetchedStatesForProcessingType <- prefetchedStates.get(process.processingType)
                // State is prefetched for all scenarios for the given processing type.
                // If there is no information available for a specific scenario name,
                // then it means that DM is not aware of this scenario, and we should default to List.empty[StatusDetails].
                prefetchedState = prefetchedStatesForProcessingType.getOrElse(process.name, List.empty)
              } yield prefetchedState
              prefetchedState match {
                case Some(prefetchedStatusDetails) =>
                  getProcessStateUsingPrefetchedStatus(
                    process.toEntity,
                    actionsInProgress.getOrElse(process.processIdUnsafe, Set.empty),
                    None,
                    prefetchedStatusDetails,
                  ).map(state => process.copy(state = Some(state)))
                case None =>
                  getProcessStateFetchingStatusFromManager(
                    process.toEntity,
                    actionsInProgress.getOrElse(process.processIdUnsafe, Set.empty),
                    None,
                  ).map(state => process.copy(state = Some(state)))
              }
          }
          .sequence[DB, ScenarioWithDetails]
      } yield processesWithState
    )
  }

  private def getProcessStateFetchingStatusFromManager(
      processDetails: ScenarioWithDetailsEntity[_],
      inProgressActionNames: Set[ScenarioActionName],
      currentlyPresentedVersionId: Option[VersionId],
  )(implicit freshnessPolicy: DataFreshnessPolicy, user: LoggedUser): DB[ProcessState] = {
    getProcessState(
      processDetails,
      inProgressActionNames,
      currentlyPresentedVersionId,
      manager =>
        getStateFromDeploymentManager(
          manager,
          processDetails.idWithName,
          processDetails.lastStateAction,
          processDetails.processVersionId,
          processDetails.lastDeployedAction.map(_.processVersionId),
          currentlyPresentedVersionId,
        )
    )
  }

  // DeploymentManager's may support fetching state of all scenarios at once
  // State is prefetched only when:
  //  - DM has capability StateQueryForAllScenariosSupported
  //  - the query is about more than one scenario handled by that DM
  private def getPrefetchedStatesForSupportedManagers(
      scenarios: List[ScenarioWithDetails],
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
      scenarios: List[ScenarioWithDetails]
  ): DB[Map[ProcessId, Set[ScenarioActionName]]] = {
    scenarios match {
      case Nil => DBIO.successful(Map.empty)
      case head :: Nil =>
        actionRepository
          .getInProgressActionNames(head.processIdUnsafe)
          .map(actionNames => Map(head.processIdUnsafe -> actionNames))
      case _ =>
        // We are getting only Deploy and Cancel InProgress actions as only these two impact ProcessState
        actionRepository.getInProgressActionNames(Set(Deploy, Cancel))
    }
  }

  private def getProcessStateUsingPrefetchedStatus(
      processDetails: ScenarioWithDetailsEntity[_],
      inProgressActionNames: Set[ScenarioActionName],
      currentlyPresentedVersionId: Option[VersionId],
      prefetchedStatusDetails: WithDataFreshnessStatus[List[StatusDetails]],
  )(implicit user: LoggedUser): DB[ProcessState] = {
    getProcessState(
      processDetails,
      inProgressActionNames,
      currentlyPresentedVersionId,
      manager =>
        manager
          .resolve(
            processDetails.idWithName,
            prefetchedStatusDetails.value,
            processDetails.lastStateAction,
            processDetails.processVersionId,
            processDetails.lastDeployedAction.map(_.processVersionId),
            currentlyPresentedVersionId,
          )
          .map(prefetchedStatusDetails.withValue)
    )
  }

  private def getProcessState(
      processDetails: ScenarioWithDetailsEntity[_],
      inProgressActionNames: Set[ScenarioActionName],
      currentlyPresentedVersionId: Option[VersionId],
      fetchState: DeploymentManager => Future[WithDataFreshnessStatus[ProcessState]],
  )(implicit user: LoggedUser): DB[ProcessState] = {
    val processVersionId  = processDetails.processVersionId
    val deployedVersionId = processDetails.lastDeployedAction.map(_.processVersionId)
    dispatcher
      .deploymentManager(processDetails.processingType)
      .map { manager =>
        if (processDetails.isFragment) {
          throw FragmentStateException
        } else if (processDetails.isArchived) {
          getArchivedProcessState(processDetails, currentlyPresentedVersionId)(manager)
        } else if (inProgressActionNames.contains(ScenarioActionName.Deploy)) {
          logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.DuringDeploy}")
          DBIOAction.successful(
            manager.processStateDefinitionManager.processState(
              StatusDetails(SimpleStateStatus.DuringDeploy, None),
              processVersionId,
              deployedVersionId,
              currentlyPresentedVersionId,
            )
          )
        } else if (inProgressActionNames.contains(ScenarioActionName.Cancel)) {
          logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.DuringCancel}")
          DBIOAction.successful(
            manager.processStateDefinitionManager.processState(
              StatusDetails(SimpleStateStatus.DuringCancel, None),
              processVersionId,
              deployedVersionId,
              currentlyPresentedVersionId,
            )
          )
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
              logger.debug(s"Status for never deployed: '${processDetails.name}' is: ${SimpleStateStatus.NotDeployed}")
              DBIOAction.successful(
                manager.processStateDefinitionManager.processState(
                  StatusDetails(SimpleStateStatus.NotDeployed, None),
                  processVersionId,
                  deployedVersionId,
                  currentlyPresentedVersionId,
                )
              )
          }
        }
      }
      .getOrElse(
        DBIOAction.successful(SimpleProcessStateDefinitionManager.errorFailedToGet(processVersionId))
      )
  }

  // We assume that checking the state for archived doesn't make sense, and we compute the state based on the last state action
  private def getArchivedProcessState(
      processDetails: ScenarioWithDetailsEntity[_],
      currentlyPresentedVersionId: Option[VersionId],
  )(implicit manager: DeploymentManager) = {
    val processVersionId  = processDetails.processVersionId
    val deployedVersionId = processDetails.lastDeployedAction.map(_.processVersionId)
    processDetails.lastStateAction.map(a => (a.actionName, a.state)) match {
      case Some((Cancel, _)) =>
        logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.Canceled}")
        DBIOAction.successful(
          manager.processStateDefinitionManager.processState(
            StatusDetails(SimpleStateStatus.Canceled, None),
            processVersionId,
            deployedVersionId,
            currentlyPresentedVersionId,
          )
        )
      case Some((Deploy, ProcessActionState.ExecutionFinished)) =>
        logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.Finished} ")
        DBIOAction.successful(
          manager.processStateDefinitionManager.processState(
            StatusDetails(SimpleStateStatus.Finished, None),
            processVersionId,
            deployedVersionId,
            currentlyPresentedVersionId,
          )
        )
      case Some(_) =>
        logger.warn(s"Status for: '${processDetails.name}' is: ${ProblemStateStatus.ArchivedShouldBeCanceled}")
        DBIOAction.successful(
          manager.processStateDefinitionManager.processState(
            StatusDetails(ProblemStateStatus.ArchivedShouldBeCanceled, None),
            processVersionId,
            deployedVersionId,
            currentlyPresentedVersionId,
          )
        )
      case _ =>
        logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.NotDeployed}")
        DBIOAction.successful(
          manager.processStateDefinitionManager.processState(
            StatusDetails(SimpleStateStatus.NotDeployed, None),
            processVersionId,
            deployedVersionId,
            currentlyPresentedVersionId,
          )
        )
    }
  }

  private def getStateFromDeploymentManager(
      deploymentManager: DeploymentManager,
      processIdWithName: ProcessIdWithName,
      lastStateAction: Option[ProcessAction],
      latestVersionId: VersionId,
      deployedVersionId: Option[VersionId],
      currentlyPresentedVersionId: Option[VersionId],
  )(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[ProcessState]] = {

    val state = deploymentManager
      .getProcessState(
        processIdWithName,
        lastStateAction,
        latestVersionId,
        deployedVersionId,
        currentlyPresentedVersionId,
      )
      .recover { case NonFatal(e) =>
        logger.warn(s"Failed to get status of ${processIdWithName.name}: ${e.getMessage}", e)
        failedToGetProcessState(latestVersionId)
      }

    scenarioStateTimeout
      .map { timeout =>
        state.withTimeout(timeout, timeoutResult = failedToGetProcessState(latestVersionId)).map {
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

  private def failedToGetProcessState(versionId: VersionId) =
    WithDataFreshnessStatus.fresh(SimpleProcessStateDefinitionManager.errorFailedToGet(versionId))

  private def existsOrFail[T](checkThisOpt: Option[T], failWith: => Exception): DB[T] = {
    checkThisOpt match {
      case Some(checked) => DBIOAction.successful(checked)
      case None          => DBIOAction.failed(failWith)
    }
  }

}
