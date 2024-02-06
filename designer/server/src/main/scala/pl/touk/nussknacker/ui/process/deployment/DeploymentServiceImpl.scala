package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import cats.Traverse
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.{toFoldableOps, toTraverseOps}
import cats.syntax.functor._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.{Cancel, Deploy, ProcessActionType}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}
import pl.touk.nussknacker.ui.api.{DeploymentCommentSettings, ListenerApiUser}
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{
  OnActionExecutionFinished,
  OnDeployActionFailed,
  OnDeployActionSuccess,
  OnFinished
}
import pl.touk.nussknacker.ui.listener.{ProcessChangeListener, User => ListenerUser}
import pl.touk.nussknacker.ui.process.ScenarioWithDetailsConversions._
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.exception.{DeployingInvalidScenarioError, ProcessIllegalAction}
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.{AdminUser, LoggedUser, NussknackerInternalUser}
import pl.touk.nussknacker.ui.util.FutureUtils._
import pl.touk.nussknacker.ui.validation.UIProcessValidator
import slick.dbio.{DBIO, DBIOAction}

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
    actionRepository: DbProcessActionRepository,
    dbioRunner: DBIOActionRunner,
    processValidator: ProcessingTypeDataProvider[UIProcessValidator, _],
    scenarioResolver: ProcessingTypeDataProvider[ScenarioResolver, _],
    processChangeListener: ProcessChangeListener,
    scenarioStateTimeout: Option[FiniteDuration],
    deploymentCommentSettings: Option[DeploymentCommentSettings],
    clock: Clock = Clock.systemUTC()
)(implicit system: ActorSystem)
    extends DeploymentService
    with LazyLogging {

  override def cancelProcess(
      processId: ProcessIdWithName,
      comment: Option[String]
  )(implicit user: LoggedUser, ec: ExecutionContext): Future[Unit] = {
    val actionType = ProcessActionType.Cancel
    checkCanPerformActionAndAddInProgressAction[Unit](
      processId,
      actionType,
      _.lastDeployedAction.map(_.processVersionId),
      _ => None
    ).flatMap { case (processDetails, actionId, versionOnWhichActionIsDone, buildInfoProcessIngType) =>
      for {
        deploymentCommentOpt <- validateDeploymentComment(comment)
        _ <- runDeploymentActionWithNotifications(
          actionType,
          actionId,
          processId,
          versionOnWhichActionIsDone,
          deploymentCommentOpt,
          buildInfoProcessIngType
        ) {
          dispatcher.deploymentManagerUnsafe(processDetails.processingType).cancel(processId.name, user.toManagerUser)
        }
      } yield ()
    }
  }

  // Inner Future in result allows to wait for deployment finish, while outer handles validation
  // We split deploy process that way because we want to be able to split FE logic into two phases:
  // - validations - it is quick part, the result will be displayed on deploy modal
  // - deployment on engine side - it is longer part, the result will be shown as a notification
  override def deployProcessAsync(
      processIdWithName: ProcessIdWithName,
      savepointPath: Option[String],
      comment: Option[String]
  )(implicit user: LoggedUser, ec: ExecutionContext): Future[Future[Option[ExternalDeploymentId]]] = {
    val actionType = ProcessActionType.Deploy
    checkCanPerformActionAndAddInProgressAction[CanonicalProcess](
      processIdWithName,
      actionType,
      d => Some(d.processVersionId),
      d => Some(d.processingType)
    ).flatMap { case (processDetails, actionId, versionOnWhichActionIsDone, buildInfoProcessIngType) =>
      for {
        deploymentCommentOpt <- validateDeploymentComment(comment)
        deployment <- validateBeforeDeploy(processDetails, actionId).transformWith {
          case Failure(ex) =>
            dbioRunner.runInTransaction(actionRepository.removeAction(actionId)).transform(_ => Failure(ex))
          case Success(validationResult) =>
            // we notify of deployment finish/fail only if initial validation succeeded
            val deploymentFuture = runDeploymentActionWithNotifications(
              actionType,
              actionId,
              processIdWithName,
              versionOnWhichActionIsDone,
              deploymentCommentOpt,
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
      } yield deployment
    }
  }

  // TODO: this is temporary step: we want ParameterValidator here. The aim is to align deployment and custom actions
  //  and validate deployment comment (and other action parameters) the same way as in node expressions or additional properties.
  private def validateDeploymentComment(comment: Option[String]): Future[Option[DeploymentComment]] =
    DeploymentComment.createDeploymentComment(comment, deploymentCommentSettings) match {
      case Valid(deploymentComment) => Future.successful(deploymentComment)
      case Invalid(exc)             => Future.failed(ValidationError(exc.getMessage))
    }

  protected def validateBeforeDeploy(
      processDetails: ScenarioWithDetailsEntity[CanonicalProcess],
      actionId: ProcessActionId
  )(implicit user: LoggedUser, ec: ExecutionContext): Future[DeployedScenarioData] = {
    for {
      _ <- Future.fromTry(Try(validateProcess(processDetails)))
      deploymentManager = dispatcher.deploymentManagerUnsafe(processDetails.processingType)
      // TODO: scenario was already resolved during validation - use it here
      resolvedCanonicalProcess <- Future.fromTry(
        scenarioResolver.forTypeUnsafe(processDetails.processingType).resolveScenario(processDetails.json)
      )
      deploymentData = prepareDeploymentData(user.toManagerUser, DeploymentId.fromActionId(actionId))
      _ <- deploymentManager.validate(processDetails.toEngineProcessVersion, deploymentData, resolvedCanonicalProcess)
    } yield DeployedScenarioData(processDetails.toEngineProcessVersion, deploymentData, resolvedCanonicalProcess)
  }

  private def validateProcess(
      processDetails: ScenarioWithDetailsEntity[CanonicalProcess]
  )(implicit user: LoggedUser): Unit = {
    val validationResult = processValidator
      .forTypeUnsafe(processDetails.processingType)
      .validateCanonicalProcess(processDetails.json, processDetails.isFragment)
    if (validationResult.hasErrors) {
      throw DeployingInvalidScenarioError(validationResult.errors)
    }
  }

  private def checkCanPerformActionAndAddInProgressAction[PS: ScenarioShapeFetchStrategy](
      processId: ProcessIdWithName,
      actionType: ProcessActionType,
      getVersionOnWhichActionIsDone: ScenarioWithDetailsEntity[PS] => Option[VersionId],
      getBuildInfoProcessingType: ScenarioWithDetailsEntity[PS] => Option[ProcessingType]
  )(
      implicit user: LoggedUser,
      ec: ExecutionContext
  ): Future[(ScenarioWithDetailsEntity[PS], ProcessActionId, Option[VersionId], Option[ProcessingType])] = {
    for {
      processDetailsOpt <- dbioRunner.run(processRepository.fetchLatestProcessDetailsForProcessId[PS](processId.id))
      processDetails    <- dbioRunner.run(existsOrFail(processDetailsOpt, ProcessNotFoundError(processId.name)))
      _                          = checkIfCanPerformActionOnProcess(actionType.toString, processDetails)
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

  private def checkInProgressWithLocking[PS: ScenarioShapeFetchStrategy](
      processDetails: ScenarioWithDetailsEntity[PS],
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

  private def checkIfCanPerformActionOnProcess[PS: ScenarioShapeFetchStrategy](
      actionName: String,
      processDetails: ScenarioWithDetailsEntity[PS]
  ): Unit = {
    if (processDetails.isArchived) {
      throw ProcessIllegalAction.archived(actionName, processDetails.name)
    } else if (processDetails.isFragment) {
      throw ProcessIllegalAction.fragment(actionName, processDetails.name)
    }
  }

  private def checkIfCanPerformActionInState[PS: ScenarioShapeFetchStrategy](
      actionType: ProcessActionType,
      processDetails: ScenarioWithDetailsEntity[PS],
      ps: ProcessState
  ): Unit = {
    if (!ps.allowedActions.contains(actionType)) {
      logger.debug(s"Action: $actionType on process: ${processDetails.name} not allowed in ${ps.status} state")
      throw ProcessIllegalAction(actionType.toString, processDetails.name, ps)
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
      processDetailsOpt     <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id)
      processDetails        <- existsOrFail(processDetailsOpt, ProcessNotFoundError(processIdWithName.name))
      inProgressActionTypes <- actionRepository.getInProgressActionTypes(processDetails.processId)
      result                <- getProcessState(processDetails, inProgressActionTypes)
    } yield result)
  }

  override def getProcessState(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit user: LoggedUser, ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState] = {
    dbioRunner.run(for {
      inProgressActionTypes <- actionRepository.getInProgressActionTypes(processDetails.processId)
      result                <- getProcessState(processDetails, inProgressActionTypes)
    } yield result)
  }

  override def enrichDetailsWithProcessState[F[_]: Traverse](processTraverse: F[ScenarioWithDetails])(
      implicit user: LoggedUser,
      ec: ExecutionContext,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[F[ScenarioWithDetails]] = {
    dbioRunner.run(
      for {
        actionsInProgress <- getInProgressActionTypesForProcessTraverse(processTraverse)
        processesWithState <- processTraverse
          .map {
            case process if process.isFragment => DBIO.successful(process)
            case process =>
              getProcessState(
                process.toEntity,
                actionsInProgress.getOrElse(process.processIdUnsafe, Set.empty)
              ).map(state => process.copy(state = Some(state)))
          }
          .sequence[DB, ScenarioWithDetails]
      } yield processesWithState
    )
  }

  // This is optimisation tweak. We want to reduce number of calls for in progress action types. So for >1 scenarios
  // we do one call for all in progress action types for all scenarios
  private def getInProgressActionTypesForProcessTraverse[F[_]: Traverse](
      processTraverse: F[ScenarioWithDetails]
  )(implicit ec: ExecutionContext): DB[Map[ProcessId, Set[ProcessActionType]]] = {
    processTraverse.toList match {
      case Nil => DBIO.successful(Map.empty)
      case head :: Nil =>
        actionRepository
          .getInProgressActionTypes(head.processIdUnsafe)
          .map(actionTypes => Map(head.processIdUnsafe -> actionTypes))
      case _ =>
        // We are getting only Deploy and Cancel InProgress actions as only these two impact ProcessState
        actionRepository.getInProgressActionTypes(Set(Deploy, Cancel))
    }
  }

  private def getProcessState(
      processDetails: ScenarioWithDetailsEntity[_],
      inProgressActionTypes: Set[ProcessActionType]
  )(implicit ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy, user: LoggedUser): DB[ProcessState] = {
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
  private def getArchivedProcessState(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit manager: DeploymentManager) = {
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
    logger.debug(s"About to mark process $processName as finished if last action was DEPLOY")
    dbioRunner.run(for {
      processIdOpt      <- processRepository.fetchProcessId(processName)
      processId         <- existsOrFail(processIdOpt, ProcessNotFoundError(processName))
      processDetailsOpt <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId)
      processDetails    <- existsOrFail(processDetailsOpt, ProcessNotFoundError(processName))
      _ = validateExpectedProcessingType(expectedProcessingType, processDetails.processingType)
      cancelActionOpt <- {
        logger.debug(s"lastDeployedAction for $processName: ${processDetails.lastDeployedAction}")
        processDetails.lastDeployedAction
          .map { lastDeployedAction =>
            logger.info(s"Marking process $processName as finished")
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
        _ <- validateExpectedProcessingType(expectedProcessingType, processId)
        lastStateAction <- actionRepository.getFinishedProcessActions(
          processId,
          Some(ProcessActionType.StateActionsTypes)
        )
      } yield lastStateAction.headOption
    }
  }

  private def validateExpectedProcessingType(expectedProcessingType: ProcessingType, processId: ProcessId)(
      implicit ec: ExecutionContext
  ): DB[Unit] = {
    implicit val user: AdminUser = NussknackerInternalUser.instance
    // TODO: We should fetch ProcessName for a given ProcessId to avoid returning synthetic id in rest responses
    val fakeProcessNameForErrorsPurpose = ProcessName(processId.value.toString)
    processRepository.fetchProcessingType(ProcessIdWithName(processId, fakeProcessNameForErrorsPurpose)).map {
      processingType =>
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

  // TODO: further changes
  //       - block two concurrent custom actions - see ManagementResourcesConcurrentSpec
  //       - see those actions in the actions table
  //       - send notifications about finished/failed custom actions
  override def invokeCustomAction(
      actionName: String,
      processIdWithName: ProcessIdWithName,
      params: Map[String, String]
  )(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): Future[CustomActionResult] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    dbioRunner.run(
      for {
        // Fetch and validate process details
        processDetailsOpt <- processRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess](
          processIdWithName.id
        )
        processDetails <- existsOrFail(processDetailsOpt, ProcessNotFoundError(processIdWithName.name))
        _ = checkIfCanPerformActionOnProcess(actionName, processDetails)
        processState <- DBIOAction.from(getProcessState(processDetails))
        manager = dispatcher.deploymentManagerUnsafe(processDetails.processingType)
        // Fetch and validate custom action details
        actionReq = CustomActionRequest(
          actionName,
          processDetails.toEngineProcessVersion,
          loggedUser.toManagerUser,
          params
        )
        customActionOpt = manager.customActions.find(_.name == actionName)
        _ <- existsOrFail(customActionOpt, CustomActionNonExisting(actionReq))
        _ = checkIfCanPerformCustomActionInState(actionName, processDetails, processState, manager)
        invokeActionResult <- DBIOAction.from(manager.invokeCustomAction(actionReq, processDetails.json))
      } yield invokeActionResult
    )
  }

  private def checkIfCanPerformCustomActionInState[PS: ScenarioShapeFetchStrategy](
      actionName: String,
      processDetails: ScenarioWithDetailsEntity[PS],
      ps: ProcessState,
      manager: DeploymentManager
  ): Unit = {
    val allowedActionsForStatus = manager.customActions.collect {
      case a if a.allowedStateStatusNames.contains(ps.status.name) => a.name
    }.distinct
    if (!allowedActionsForStatus.contains(actionName)) {
      logger.debug(s"Action: $actionName on process: ${processDetails.name} not allowed in ${ps.status} state")
      throw ProcessIllegalAction(actionName, processDetails.name, ps.status.name, allowedActionsForStatus)
    }
  }

}

private class FragmentStateException extends BadRequestError("Fragment doesn't have state.")

private case class CustomActionNonExisting(req: CustomActionRequest)
    extends NotFoundError(s"${req.name} is not existing")

case class ValidationError(message: String) extends BadRequestError(message)
