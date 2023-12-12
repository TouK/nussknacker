package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.{Cancel, Deploy, ProcessActionType}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy, StateActionsTypes}
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.api.ListenerApiUser
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{
  OnActionExecutionFinished,
  OnDeployActionFailed,
  OnDeployActionSuccess,
  OnFinished
}
import pl.touk.nussknacker.ui.listener.{ProcessChangeListener, User => ListenerUser}
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.exception.{DeployingInvalidScenarioError, ProcessIllegalAction}
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.{AdminUser, LoggedUser, NussknackerInternalUser}
import pl.touk.nussknacker.ui.util.FutureUtils._
import pl.touk.nussknacker.ui.validation.ProcessValidation
import slick.dbio.DBIOAction

import java.time.Clock
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

// Responsibility of this class is to wrap communication with DeploymentManager with persistent, transactional context.
// It ensures that all actions are done consistently: do validations and ensures that only allowed actions
// will be executed in given state. It sends notifications about finished actions.
// Also thanks to it we are able to check if state on remote engine is the same as persisted state.
class DeploymentServiceImpl(
    dispatcher: DeploymentManagerDispatcher,
    processRepository: FetchingProcessRepository[DB],
    actionRepository: DbProcessActionRepository[DB],
    dbioRunner: DBIOActionRunner,
    processValidation: ProcessValidation,
    scenarioResolver: ScenarioResolver,
    processChangeListener: ProcessChangeListener,
    scenarioStateTimeout: Option[FiniteDuration],
    clock: Clock = Clock.systemUTC()
)(implicit system: ActorSystem)
    extends DeploymentService
    with LazyLogging {

  def getDeployedScenarios(
      processingType: ProcessingType
  )(implicit ec: ExecutionContext): Future[List[DeployedScenarioData]] = {
    for {
      deployedProcesses <- {
        implicit val userFetchingDataFromRepository: LoggedUser = NussknackerInternalUser.instance
        dbioRunner.run(
          processRepository.fetchProcessesDetails[CanonicalProcess](
            FetchProcessesDetailsQuery(
              isFragment = Some(false),
              isArchived = Some(false),
              isDeployed = Some(true),
              processingTypes = Some(Seq(processingType))
            )
          )
        )
      }
      dataList <- Future.sequence(deployedProcesses.flatMap { details =>
        val lastDeployAction = details.lastDeployedAction.get
        // TODO: what should be in name?
        val deployingUser  = User(lastDeployAction.user, lastDeployAction.user)
        val deploymentData = prepareDeploymentData(deployingUser, DeploymentId.fromActionId(lastDeployAction.id))
        val deployedScenarioDataTry =
          scenarioResolver.resolveScenario(details.json, details.processCategory).map { resolvedScenario =>
            DeployedScenarioData(
              details.toEngineProcessVersion.copy(versionId = lastDeployAction.processVersionId),
              deploymentData,
              resolvedScenario
            )
          }
        deployedScenarioDataTry match {
          case Failure(exception) =>
            logger.error(s"Exception during resolving deployed scenario ${details.id}", exception)
            None
          case Success(value) => Some(Future.successful(value))
        }
      })
    } yield dataList
  }

  override def cancelProcess(
      processId: ProcessIdWithName,
      deploymentComment: Option[DeploymentComment]
  )(implicit user: LoggedUser, ec: ExecutionContext): Future[Unit] = {
    val actionType = ProcessActionType.Cancel
    checkCanPerformActionAndAddInProgressAction[Unit](
      processId.id,
      actionType,
      _.lastDeployedAction.map(_.processVersionId),
      _ => None
    ).flatMap { case (processDetails, actionId, versionOnWhichActionIsDone, buildInfoProcessIngType) =>
      runDeploymentActionWithNotifications(
        actionType,
        actionId,
        processId,
        versionOnWhichActionIsDone,
        deploymentComment,
        buildInfoProcessIngType
      ) {
        dispatcher.deploymentManagerUnsafe(processDetails.processingType).cancel(processId.name, user.toManagerUser)
      }
    }
  }

  // Inner Future in result allows to wait for deployment finish, while outer handles validation
  // We split deploy process that way because we want to be able to split FE logic into two phases:
  // - validations - it is quick part, the result will be displayed on deploy modal
  // - deployment on engine side - it is longer part, the result will be shown as a notification
  override def deployProcessAsync(
      processIdWithName: ProcessIdWithName,
      savepointPath: Option[String],
      deploymentComment: Option[DeploymentComment]
  )(implicit user: LoggedUser, ec: ExecutionContext): Future[Future[Option[ExternalDeploymentId]]] = {
    val actionType = ProcessActionType.Deploy
    checkCanPerformActionAndAddInProgressAction[CanonicalProcess](
      processIdWithName.id,
      actionType,
      d => Some(d.processVersionId),
      d => Some(d.processingType)
    ).flatMap { case (processDetails, actionId, versionOnWhichActionIsDone, buildInfoProcessIngType) =>
      validateBeforeDeploy(processDetails, actionId).transformWith {
        case Failure(ex) =>
          dbioRunner.runInTransaction(actionRepository.removeAction(actionId)).transform(_ => Failure(ex))
        case Success(validationResult) =>
          // we notify of deployment finish/fail only if initial validation succeeded
          val deploymentFuture = runDeploymentActionWithNotifications(
            actionType,
            actionId,
            processIdWithName,
            versionOnWhichActionIsDone,
            deploymentComment,
            buildInfoProcessIngType
          ) {
            dispatcher
              .deploymentManagerUnsafe(processDetails.processingType)
              .deploy(
                validationResult.processVersion,
                validationResult.deploymentData,
                validationResult.resolvedScenario,
                savepointPath
              )
          }
          Future.successful(deploymentFuture)
      }
    }
  }

  protected def validateBeforeDeploy(
      processDetails: BaseProcessDetails[CanonicalProcess],
      actionId: ProcessActionId
  )(implicit user: LoggedUser, ec: ExecutionContext): Future[DeployedScenarioData] = {
    for {
      _ <- Future.fromTry(Try(validateProcess(processDetails)))
      deploymentManager = dispatcher.deploymentManagerUnsafe(processDetails.processingType)
      // TODO: scenario was already resolved during validation - use it here
      resolvedCanonicalProcess <- Future.fromTry(
        scenarioResolver.resolveScenario(processDetails.json, processDetails.processCategory)
      )
      deploymentData = prepareDeploymentData(user.toManagerUser, DeploymentId.fromActionId(actionId))
      _ <- deploymentManager.validate(processDetails.toEngineProcessVersion, deploymentData, resolvedCanonicalProcess)
    } yield DeployedScenarioData(processDetails.toEngineProcessVersion, deploymentData, resolvedCanonicalProcess)
  }

  private def validateProcess(processDetails: BaseProcessDetails[CanonicalProcess]): Unit = {
    val validationResult = processValidation.processingTypeValidationWithTypingInfo(
      processDetails.json,
      processDetails.processingType,
      processDetails.processCategory
    )
    if (validationResult.hasErrors) throw DeployingInvalidScenarioError
  }

  private def checkCanPerformActionAndAddInProgressAction[PS: ProcessShapeFetchStrategy](
      processId: ProcessId,
      actionType: ProcessActionType,
      getVersionOnWhichActionIsDone: BaseProcessDetails[PS] => Option[VersionId],
      getBuildInfoProcessingType: BaseProcessDetails[PS] => Option[ProcessingType]
  )(
      implicit user: LoggedUser,
      ec: ExecutionContext
  ): Future[(BaseProcessDetails[PS], ProcessActionId, Option[VersionId], Option[ProcessingType])] = {
    for {
      processDetailsOpt <- dbioRunner.run(processRepository.fetchLatestProcessDetailsForProcessId[PS](processId))
      processDetails <- dbioRunner.run(existsOrFail(processDetailsOpt, ProcessNotFoundError(processId.value.toString)))
      _                          = checkIfCanPerformActionOnProcess(actionType, processDetails)
      versionOnWhichActionIsDone = getVersionOnWhichActionIsDone(processDetails)
      buildInfoProcessIngType    = getBuildInfoProcessingType(processDetails)
      // We wrap only in progress action adding to avoid long transactions and potential deadlocks
      actionId <- checkInProgressWithLocking(
        processDetails,
        actionType,
        versionOnWhichActionIsDone,
        buildInfoProcessIngType
      )
    } yield (processDetails, actionId, versionOnWhichActionIsDone, buildInfoProcessIngType)
  }

  private def checkInProgressWithLocking[PS: ProcessShapeFetchStrategy](
      processDetails: BaseProcessDetails[PS],
      actionType: ProcessActionType,
      versionOnWhichActionIsDone: Option[VersionId],
      buildInfoProcessIngType: Option[ProcessingType]
  )(implicit user: LoggedUser, ec: ExecutionContext): Future[ProcessActionId] = {
    dbioRunner.runInTransaction(for {
      _                     <- actionRepository.lockActionsTable
      inProgressActionTypes <- actionRepository.getInProgressActionTypes(processDetails.processId)
      processState <- {
        implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
        getProcessState(processDetails, inProgressActionTypes)
      }
      _ = checkIfCanPerformActionInState(actionType, processDetails, processState)
      actionId <- actionRepository.addInProgressAction(
        processDetails.processId,
        actionType,
        versionOnWhichActionIsDone,
        buildInfoProcessIngType
      )
    } yield actionId)
  }

  private def checkIfCanPerformActionOnProcess[PS: ProcessShapeFetchStrategy](
      actionType: ProcessActionType,
      processDetails: BaseProcessDetails[PS]
  ): Unit = {
    if (processDetails.isArchived) {
      throw ProcessIllegalAction.archived(actionType, processDetails.idWithName)
    } else if (processDetails.isFragment) {
      throw ProcessIllegalAction.fragment(actionType, processDetails.idWithName)
    }
  }

  private def checkIfCanPerformActionInState[PS: ProcessShapeFetchStrategy](
      actionType: ProcessActionType,
      processDetails: BaseProcessDetails[PS],
      ps: ProcessState
  ): Unit = {
    if (!ps.allowedActions.contains(actionType)) {
      logger.debug(s"Action: $actionType on process: ${processDetails.name} not allowed in ${ps.status} state")
      throw ProcessIllegalAction(actionType, processDetails.idWithName, ps)
    }
  }

  protected def prepareDeploymentData(user: User, deploymentId: DeploymentId): DeploymentData = {
    DeploymentData(deploymentId, user, Map.empty)
  }

  private def runDeploymentActionWithNotifications[T](
      actionType: ProcessActionType,
      actionId: ProcessActionId,
      processIdWithName: ProcessIdWithName,
      versionOnWhichActionIsDoneOpt: Option[VersionId],
      deploymentComment: Option[DeploymentComment],
      buildInfoProcessIngType: Option[ProcessingType]
  )(runAction: => Future[T])(implicit user: LoggedUser, ec: ExecutionContext): Future[T] = {
    implicit val listenerUser: ListenerUser = ListenerApiUser(user)
    val actionFuture                        = runAction
    val actionString = s"${actionType.toString.toLowerCase} (id: ${actionId.value}) of ${processIdWithName.name}"
    actionFuture.transformWith {
      case Failure(failure) =>
        logger.error(s"Action: $actionString finished with failure", failure)
        val performedAt = clock.instant()
        processChangeListener.handle(OnDeployActionFailed(processIdWithName.id, failure))
        dbioRunner
          .runInTransaction(
            actionRepository.markActionAsFailed(
              actionId,
              processIdWithName.id,
              actionType,
              versionOnWhichActionIsDoneOpt,
              performedAt,
              failure.getMessage,
              buildInfoProcessIngType
            )
          )
          .transform(_ => Failure(failure))
      case Success(result) =>
        versionOnWhichActionIsDoneOpt
          .map { versionOnWhichActionIsDone =>
            logger.info(s"Finishing $actionString")
            val performedAt = clock.instant()
            val comment     = deploymentComment.map(_.toComment(actionType))
            processChangeListener.handle(
              OnDeployActionSuccess(processIdWithName.id, versionOnWhichActionIsDone, comment, performedAt, actionType)
            )
            dbioRunner.runInTransaction(
              actionRepository.markActionAsFinished(
                actionId,
                processIdWithName.id,
                actionType,
                versionOnWhichActionIsDone,
                performedAt,
                comment,
                buildInfoProcessIngType
              )
            )
          }
          .getOrElse {
            // Currently we don't send notifications and don't add finished action into db if version id is missing.
            // It happens that way during cancel when there was no finished deploy before it, but some scenario is running on engine
            // TODO: We should send notifications and add action db entry in that cases as well as for normal finish.
            //       Before we can do that we should check if we somewhere rely on fact that version is always defined -
            //       see ProcessAction.processVersionId
            logger.info(s"Action $actionString finished for action without version id - skipping listener notification")
            dbioRunner.runInTransaction(actionRepository.removeAction(actionId))
          }
          .map(_ => result)
    }
  }

  // TODO: check deployment id to be sure that returned status is for given deployment
  override def getProcessState(
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser, ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState] = {
    dbioRunner.run(for {
      processDetailsOpt <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id)
      processDetails    <- existsOrFail(processDetailsOpt, ProcessNotFoundError(processIdWithName.id.value.toString))
      inProgressActionTypes <- actionRepository.getInProgressActionTypes(processDetails.processId)
      result                <- getProcessState(processDetails, inProgressActionTypes)
    } yield result)
  }

  override def getProcessState(
      processDetails: BaseProcessDetails[_]
  )(implicit user: LoggedUser, ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState] = {
    dbioRunner.run(for {
      inProgressActionTypes <- actionRepository.getInProgressActionTypes(processDetails.processId)
      result                <- getProcessState(processDetails, inProgressActionTypes)
    } yield result)
  }

  override def fetchProcessStatesForProcesses(processes: List[BaseProcessDetails[Unit]])(
      implicit user: LoggedUser,
      ec: ExecutionContext,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[Map[String, ProcessState]] =
    for {
      processesInProgress <- getInProgressActionTypesForAllProcesses
      processStatus <- processes
        .map(process => Future.successful(process.name) zip getProcessState(process, processesInProgress))
        .sequence
    } yield processStatus.toMap

  override def enrichDetailsWithProcessState(processList: List[BaseProcessDetails[_]])(
      implicit user: LoggedUser,
      ec: ExecutionContext,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[List[BaseProcessDetails[_]]] =
    for {
      actionsInProgress <- getInProgressActionTypesForAllProcesses
      processesWithState <- processList.map {
        case process if process.isFragment => Future.successful((process, None))
        case process => Future.successful(process) zip getProcessState(process, actionsInProgress).map(Option(_))
      }.sequence
    } yield processesWithState.map { case (process, state) => process.copy(state = state) }

  // We are getting only Deploy and Cancel InProgress actions as only these two impact ProcessState
  private def getInProgressActionTypesForAllProcesses: Future[Map[ProcessId, Set[ProcessActionType]]] =
    dbioRunner.run(
      actionRepository.getInProgressActionTypes(Set(Deploy, Cancel))
    )

  private def getProcessState(
      processDetails: BaseProcessDetails[_],
      processesInProgress: Map[ProcessId, Set[ProcessActionType]]
  )(implicit ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState] =
    dbioRunner.run(
      getProcessState(processDetails, processesInProgress.getOrElse(processDetails.processId, Set.empty))
    )

  private def getProcessState(
      processDetails: BaseProcessDetails[_],
      inProgressActionTypes: Set[ProcessActionType]
  )(implicit ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): DB[ProcessState] = {
    dispatcher
      .deploymentManager(processDetails.processingType)
      .map { manager =>
        if (processDetails.isFragment) {
          throw new FragmentStateException
        } else if (processDetails.isArchived) {
          getArchivedProcessState(processDetails)(manager)
        } else if (inProgressActionTypes.contains(ProcessActionType.Deploy)) {
          logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.DuringDeploy}")
          DBIOAction.successful(
            manager.processStateDefinitionManager.processState(StatusDetails(SimpleStateStatus.DuringDeploy, None))
          )
        } else if (inProgressActionTypes.contains(ProcessActionType.Cancel)) {
          logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.DuringCancel}")
          DBIOAction.successful(
            manager.processStateDefinitionManager.processState(StatusDetails(SimpleStateStatus.DuringCancel, None))
          )
        } else {
          processDetails.lastStateAction match {
            case Some(_) =>
              DBIOAction
                .from(getStateFromDeploymentManager(manager, processDetails.idWithName, processDetails.lastStateAction))
                .map { statusWithFreshness =>
                  logger.debug(
                    s"Status for: '${processDetails.name}' is: ${statusWithFreshness.value.status}, cached: ${statusWithFreshness.cached}, last status action: ${processDetails.lastStateAction
                        .map(_.actionType)})"
                  )
                  statusWithFreshness.value
                }
            case _ => // We assume that the process never deployed should have no state at the engine
              logger.debug(s"Status for never deployed: '${processDetails.name}' is: ${SimpleStateStatus.NotDeployed}")
              DBIOAction.successful(
                manager.processStateDefinitionManager.processState(StatusDetails(SimpleStateStatus.NotDeployed, None))
              )
          }
        }
      }
      .getOrElse(DBIOAction.successful(SimpleProcessStateDefinitionManager.ErrorFailedToGet))
  }

  // We assume that checking the state for archived doesn't make sense, and we compute the state based on the last state action
  private def getArchivedProcessState(processDetails: BaseProcessDetails[_])(implicit manager: DeploymentManager) = {
    processDetails.lastStateAction.map(a => (a.actionType, a.state)) match {
      case Some((Cancel, _)) =>
        logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.Canceled}")
        DBIOAction.successful(
          manager.processStateDefinitionManager.processState(StatusDetails(SimpleStateStatus.Canceled, None))
        )
      case Some((Deploy, ProcessActionState.ExecutionFinished)) =>
        logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.Finished} ")
        DBIOAction.successful(
          manager.processStateDefinitionManager.processState(StatusDetails(SimpleStateStatus.Finished, None))
        )
      case Some(_) =>
        logger.warn(s"Status for: '${processDetails.name}' is: ${ProblemStateStatus.ArchivedShouldBeCanceled}")
        DBIOAction.successful(
          manager.processStateDefinitionManager.processState(
            StatusDetails(ProblemStateStatus.ArchivedShouldBeCanceled, None)
          )
        )
      case _ =>
        logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.NotDeployed}")
        DBIOAction.successful(
          manager.processStateDefinitionManager.processState(StatusDetails(SimpleStateStatus.NotDeployed, None))
        )
    }
  }

  private def existsOrFail[T](checkThisOpt: Option[T], failWith: Exception): DB[T] = {
    checkThisOpt match {
      case Some(checked) => DBIOAction.successful(checked)
      case None          => DBIOAction.failed(failWith)
    }
  }

  private def getStateFromDeploymentManager(
      deploymentManager: DeploymentManager,
      processIdWithName: ProcessIdWithName,
      lastStateAction: Option[ProcessAction]
  )(
      implicit ec: ExecutionContext,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[ProcessState]] = {

    val state = deploymentManager.getProcessState(processIdWithName, lastStateAction).recover { case NonFatal(e) =>
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

  def markProcessFinishedIfLastActionDeploy(expectedProcessingType: ProcessingType, processName: ProcessName)(
      implicit ec: ExecutionContext
  ): Future[Option[ProcessAction]] = {
    implicit val user: AdminUser            = NussknackerInternalUser.instance
    implicit val listenerUser: ListenerUser = ListenerApiUser(user)
    logger.debug(s"About to mark process ${processName.value} as finished if last action was DEPLOY")
    dbioRunner.run(for {
      processIdOpt      <- processRepository.fetchProcessId(processName)
      processId         <- existsOrFail(processIdOpt, ProcessNotFoundError(processName.value))
      processDetailsOpt <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId)
      processDetails    <- existsOrFail(processDetailsOpt, ProcessNotFoundError(processId.value.toString))
      _ = validateExpectedProcessingType(expectedProcessingType, processDetails.processingType)
      cancelActionOpt <- {
        logger.debug(s"lastDeployedAction for ${processName.value}: ${processDetails.lastDeployedAction}")
        processDetails.lastDeployedAction
          .map { lastDeployedAction =>
            logger.info(s"Marking process ${processName.value} as finished")
            val finishedComment = DeploymentComment.unsafe("Scenario finished").toComment(ProcessActionType.Cancel)
            processChangeListener.handle(OnFinished(processDetails.processId, lastDeployedAction.processVersionId))
            actionRepository
              .addInstantAction(
                processDetails.processId,
                lastDeployedAction.processVersionId,
                ProcessActionType.Cancel,
                Some(finishedComment),
                None
              )
              .map(Some(_))

          }
          .getOrElse(DBIOAction.successful(None))
      }
    } yield cancelActionOpt)
  }

  override def markActionExecutionFinished(processingType: ProcessingType, actionId: ProcessActionId)(
      implicit ec: ExecutionContext
  ): Future[Boolean] = {
    implicit val user: AdminUser            = NussknackerInternalUser.instance
    implicit val listenerUser: ListenerUser = ListenerApiUser(user)
    logger.debug(s"About to mark action ${actionId.value} as execution finished")
    dbioRunner.runInTransaction(actionRepository.getFinishedProcessAction(actionId).flatMap { actionOpt =>
      DBIOAction
        .sequenceOption(actionOpt.map { action =>
          processChangeListener.handle(OnActionExecutionFinished(action.id, action.processId, action.processVersionId))
          doMarkActionExecutionFinished(action, processingType)
        })
        .map(_.getOrElse(false))
    })
  }

  private def doMarkActionExecutionFinished(action: ProcessAction, expectedProcessingType: ProcessingType)(
      implicit ec: ExecutionContext
  ) = {
    for {
      _            <- validateExpectedProcessingType(expectedProcessingType, action.processId)
      updateResult <- actionRepository.markFinishedActionAsExecutionFinished(action.id)
    } yield updateResult
  }

  override def getLastStateAction(expectedProcessingType: ProcessingType, processId: ProcessId)(
      implicit ec: ExecutionContext
  ): Future[Option[ProcessAction]] = {
    dbioRunner.run {
      for {
        _               <- validateExpectedProcessingType(expectedProcessingType, processId)
        lastStateAction <- actionRepository.getFinishedProcessActions(processId, Some(StateActionsTypes))
      } yield lastStateAction.headOption
    }
  }

  private def validateExpectedProcessingType(expectedProcessingType: ProcessingType, processId: ProcessId)(
      implicit ec: ExecutionContext
  ): DB[Unit] = {
    implicit val user: AdminUser = NussknackerInternalUser.instance
    processRepository.fetchProcessingType(processId).map { processingType =>
      validateExpectedProcessingType(expectedProcessingType, processingType)
    }
  }

  private def validateExpectedProcessingType(
      expectedProcessingType: ProcessingType,
      processingType: ProcessingType
  ): Unit = {
    if (processingType != expectedProcessingType) {
      throw new IllegalArgumentException(
        s"Invalid scenario processingType (expected $expectedProcessingType, got $processingType)"
      )
    }
  }

  private lazy val failedToGetProcessState =
    WithDataFreshnessStatus(SimpleProcessStateDefinitionManager.ErrorFailedToGet, cached = false)

  // It is very naive implementation for situation when designer was restarted after spawning some long running action
  // like deploy but before marking it as finished. Without this, user will always see "during deploy" status - even
  // if scenario was finished.
  // This implementation won't work correctly for >1 designer and rolling updates. Correct iplementation should invalidate
  // only actions that were spawned by inactive designer or we should return running status even if local state
  // is "during deploy" or we should periodically synchronize local state with remote state and replace local state with the remote one.
  def invalidateInProgressActions(): Unit = {
    Await.result(dbioRunner.run(actionRepository.deleteInProgressActions()), 10 seconds)
  }

}

private class FragmentStateException extends Exception("Fragment doesn't have state.") with BadRequestError
