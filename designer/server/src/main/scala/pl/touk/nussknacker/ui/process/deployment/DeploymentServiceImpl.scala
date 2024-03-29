package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import cats.Traverse
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.{toFoldableOps, toTraverseOps}
import cats.syntax.functor._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName.{Cancel, Deploy}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{
  CustomActionResult,
  DeploymentData,
  DeploymentId,
  ExternalDeploymentId,
  User
}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.api.{DeploymentCommentSettings, ListenerApiUser}
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{
  OnActionExecutionFinished,
  OnDeployActionFailed,
  OnDeployActionSuccess
}
import pl.touk.nussknacker.ui.listener.{ProcessChangeListener, User => ListenerUser}
import pl.touk.nussknacker.ui.process.ScenarioWithDetailsConversions._
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.exception.{DeployingInvalidScenarioError, ProcessIllegalAction}
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.{AdminUser, LoggedUser, NussknackerInternalUser}
import pl.touk.nussknacker.ui.util.FutureUtils._
import pl.touk.nussknacker.ui.validation.UIProcessValidator
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}
import slick.dbio.{DBIO, DBIOAction}

import java.time.Clock
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scala.language.higherKinds

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
    val actionName = ScenarioActionName.Cancel

    for {
      // 1. common for all commands/actions
      // TODO: extract param definitions to do generic "parameter validation"
      // During cancel we refer to the version that is deployed (see lastDeployedAction). In some cases, when action fails
      // and deployment continues on flink, lastDeployedAction is empty. Then we allow cancel action to proceed, to cancel
      // a running job. In that case there is no deploy action and action cancel is removed.
      // TODO: This inconsistent action-state handling needs a fix.
      _ <- validateDeploymentComment(comment)
      ctx <- prepareCommandContextWithAction[Unit](
        processId,
        actionName,
        p => p.lastDeployedAction.map(_.processVersionId),
        _ => None
      )
      // 2. command specific section
      deploymentComment = comment.map(DeploymentComment.unsafe)
      command           = CancelScenarioCommand(processId.name, user.toManagerUser)
      // 3. common for all commands/actions
      _ = runActionAndHandleResults(
        actionName,
        deploymentComment,
        ctx
      ) {
        dispatcher
          .deploymentManagerUnsafe(ctx.latestScenarioDetails.processingType)
          .processCommand(command)
      }.map(_ => ())
    } yield ()
  }

  // Inner Future in result allows to wait for deployment finish, while outer handles validation
  // We split deploy process that way because we want to be able to split FE logic into two phases:
  // - validations - it is quick part, the result will be displayed on deploy modal
  // - deployment on engine side - it is longer part, the result will be shown as a notification
  override def deployProcessAsync(
      processId: ProcessIdWithName,
      savepointPath: Option[String],
      comment: Option[String]
  )(implicit user: LoggedUser, ec: ExecutionContext): Future[Future[Option[ExternalDeploymentId]]] = {
    val actionName = ScenarioActionName.Deploy

    for {
      // 1. common for all commands/actions
      validatedComment <- validateDeploymentComment(comment)
      ctx <- prepareCommandContextWithAction[CanonicalProcess](
        processId,
        actionName,
        p => Some(p.processVersionId),
        p => Some(p.processingType)
      )
      // 2. command specific section
      deployedScenarioData <- prepareDeployedScenarioData(ctx.latestScenarioDetails, ctx.actionId)
      command = RunDeploymentCommand(
        deployedScenarioData.processVersion,
        deployedScenarioData.deploymentData,
        deployedScenarioData.resolvedScenario,
        savepointPath
      )
      // TODO: move validateBeforeDeploy before creating an action
      result <- validateBeforeDeploy(ctx.latestScenarioDetails, deployedScenarioData).transformWith {
        case Failure(ex) =>
          removeInvalidAction(ctx.actionId).transform(_ => Failure(ex))
        case Success(_) =>
          // we notify of deployment finish/fail only if initial validation succeeded
          val deploymentFuture = runActionAndHandleResults(
            actionName,
            validatedComment,
            ctx
          ) {
            dispatcher
              .deploymentManagerUnsafe(ctx.latestScenarioDetails.processingType)
              .processCommand(command)
          }
          Future.successful(deploymentFuture)
      }
    } yield result
  }

  /**
    * Common validations and operations for a command execution.
    * @return gathered data for further command execution
    */
  private def prepareCommandContextWithAction[PS: ScenarioShapeFetchStrategy](
      processId: ProcessIdWithName,
      actionName: ScenarioActionName,
      getVersionOnWhichActionIsDone: ScenarioWithDetailsEntity[PS] => Option[VersionId],
      getBuildInfoProcessingType: ScenarioWithDetailsEntity[PS] => Option[ProcessingType]
  )(implicit user: LoggedUser, ec: ExecutionContext): Future[CommandContext[PS]] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    dbioRunner.runInTransaction(
      for {
        // 1.1 TODO common parameter validations
        // 1.2 lock for critical section
        _ <- actionRepository.lockActionsTable
        // 1.3. fetch scenario data
        processDetailsOpt <- processRepository.fetchLatestProcessDetailsForProcessId[PS](processId.id)
        processDetails    <- existsOrFail(processDetailsOpt, ProcessNotFoundError(processId.name))
        // 1.4. calculate which scenario version is affected by the action: latest for deploy, deployed for cancel
        versionOnWhichActionIsDone = getVersionOnWhichActionIsDone(processDetails)
        buildInfoProcessingType    = getBuildInfoProcessingType(processDetails)
        // 1.5. check if action is performed on proper scenario (not fragment, not archived)
        _ = checkIfCanPerformActionOnScenario(actionName, processDetails)
        // 1.6. check if action is allowed for current state
        inProgressActionNames <- actionRepository.getInProgressActionNames(processDetails.processId)
        processState          <- getProcessState(processDetails, inProgressActionNames)
        _ = checkIfCanPerformActionInState(actionName, processDetails, processState)
        // 1.7. create new action, action is started with "in progress" state, the whole command execution can take some time
        actionId <- actionRepository.addInProgressAction(
          processDetails.processId,
          actionName,
          versionOnWhichActionIsDone,
          buildInfoProcessingType
        )
      } yield CommandContext(processDetails, actionId, versionOnWhichActionIsDone, buildInfoProcessingType)
    )
  }

  // TODO: Use buildInfo explicitly instead of ProcessingType-that-is-used-to-calculate-buildInfo
  private case class CommandContext[PS: ScenarioShapeFetchStrategy](
      latestScenarioDetails: ScenarioWithDetailsEntity[PS],
      actionId: ProcessActionId,
      versionOnWhichActionIsDone: Option[VersionId],
      buildInfoProcessingType: Option[ProcessingType]
  )

  // TODO: this is temporary step: we want ParameterValidator here. The aim is to align deployment and custom actions
  //  and validate deployment comment (and other action parameters) the same way as in node expressions or additional properties.
  private def validateDeploymentComment(comment: Option[String]): Future[Option[DeploymentComment]] =
    DeploymentComment.createDeploymentComment(comment, deploymentCommentSettings) match {
      case Valid(deploymentComment) => Future.successful(deploymentComment)
      case Invalid(exc)             => Future.failed(ValidationError(exc.getMessage))
    }

  protected def validateBeforeDeploy(
      processDetails: ScenarioWithDetailsEntity[CanonicalProcess],
      deployedScenarioData: DeployedScenarioData
  )(implicit user: LoggedUser, ec: ExecutionContext): Future[Unit] = {
    for {
      // 1. check scenario has no errors
      _ <- Future {
        processValidator
          .forTypeUnsafe(processDetails.processingType)
          .validateCanonicalProcess(processDetails.json, processDetails.isFragment)
      }.flatMap {
        case validationResult if validationResult.hasErrors =>
          Future.failed(DeployingInvalidScenarioError(validationResult.errors))
        case _ => Future.successful(())
      }
      // 2. check
      // TODO: scenario was already resolved during validation - use it here
      _ <- dispatcher
        .deploymentManagerUnsafe(processDetails.processingType)
        .processCommand(
          ValidateScenarioCommand(
            processDetails.toEngineProcessVersion,
            deployedScenarioData.deploymentData,
            deployedScenarioData.resolvedScenario
          )
        )
    } yield ()
  }

  private def checkIfCanPerformActionOnScenario[PS: ScenarioShapeFetchStrategy](
      actionName: ScenarioActionName,
      processDetails: ScenarioWithDetailsEntity[PS]
  ): Unit = {
    if (processDetails.isArchived) {
      throw ProcessIllegalAction.archived(actionName, processDetails.name)
    } else if (processDetails.isFragment) {
      throw ProcessIllegalAction.fragment(actionName, processDetails.name)
    }
  }

  private def checkIfCanPerformActionInState[PS: ScenarioShapeFetchStrategy](
      actionName: ScenarioActionName,
      processDetails: ScenarioWithDetailsEntity[PS],
      ps: ProcessState
  )(implicit user: LoggedUser): Unit = {
    val allowedActions = allowedActionsForState(processDetails, ps)
    if (!allowedActions.contains(actionName)) {
      logger.debug(s"Action: $actionName on process: ${processDetails.name} not allowed in ${ps.status} state")
      throw ProcessIllegalAction(actionName, processDetails.name, ps.status.name, allowedActions)
    }
  }

  private def allowedActionsForState[PS: ScenarioShapeFetchStrategy](
      processDetails: ScenarioWithDetailsEntity[PS],
      ps: ProcessState
  )(implicit user: LoggedUser): Set[ScenarioActionName] = {
    val actionsDefinedInState = ps.allowedActions.toSet
    val actionsDefinedInCustomActions = dispatcher
      .deploymentManagerUnsafe(processDetails.processingType)
      .customActionsDefinitions
      .collect {
        case a if a.allowedStateStatusNames.contains(ps.status.name) => a.actionName
      }
      .toSet
    actionsDefinedInState ++ actionsDefinedInCustomActions
  }

  protected def prepareDeployedScenarioData(
      processDetails: ScenarioWithDetailsEntity[CanonicalProcess],
      actionId: ProcessActionId,
      additionalDeploymentData: Map[String, String] = Map.empty
  )(implicit user: LoggedUser, ec: ExecutionContext): Future[DeployedScenarioData] = {
    for {
      resolvedCanonicalProcess <- Future.fromTry(
        scenarioResolver.forTypeUnsafe(processDetails.processingType).resolveScenario(processDetails.json)
      )
      deploymentData = prepareDeploymentData(
        user.toManagerUser,
        DeploymentId.fromActionId(actionId),
        additionalDeploymentData
      )
    } yield DeployedScenarioData(processDetails.toEngineProcessVersion, deploymentData, resolvedCanonicalProcess)
  }

  protected def prepareDeploymentData(
      user: User,
      deploymentId: DeploymentId,
      additionalDeploymentData: Map[String, String]
  ): DeploymentData = {
    DeploymentData(deploymentId, user, additionalDeploymentData)
  }

  private def runActionAndHandleResults[T, PS: ScenarioShapeFetchStrategy](
      actionName: ScenarioActionName,
      deploymentComment: Option[DeploymentComment],
      ctx: CommandContext[PS]
  )(runAction: => Future[T])(implicit user: LoggedUser, ec: ExecutionContext): Future[T] = {
    implicit val listenerUser: ListenerUser = ListenerApiUser(user)
    val actionFuture                        = runAction
    val actionString =
      s"${actionName.toString.toLowerCase} (id: ${ctx.actionId.value}) of ${ctx.latestScenarioDetails.name}"
    actionFuture.transformWith {
      case Failure(exception) =>
        logger.error(s"Action: $actionString finished with failure", exception)
        val performedAt = clock.instant()
        // TODO: rename to OnActionFailed
        processChangeListener.handle(OnDeployActionFailed(ctx.latestScenarioDetails.processId, exception))
        dbioRunner
          .runInTransaction(
            actionRepository.markActionAsFailed(
              ctx.actionId,
              ctx.latestScenarioDetails.processId,
              actionName,
              ctx.versionOnWhichActionIsDone,
              performedAt,
              exception.getMessage,
              ctx.buildInfoProcessingType
            )
          )
          .transform(_ => Failure(exception))
      case Success(result) =>
        ctx.versionOnWhichActionIsDone
          .map { versionOnWhichActionIsDone =>
            logger.info(s"Finishing $actionString")
            val performedAt = clock.instant()
            val comment     = deploymentComment.map(_.toComment(actionName))
            // TODO: rename to OnActionSuccess
            processChangeListener.handle(
              OnDeployActionSuccess(
                ctx.latestScenarioDetails.processId,
                versionOnWhichActionIsDone,
                comment,
                performedAt,
                actionName
              )
            )
            dbioRunner.runInTransaction(
              actionRepository.markActionAsFinished(
                ctx.actionId,
                ctx.latestScenarioDetails.processId,
                actionName,
                versionOnWhichActionIsDone,
                performedAt,
                comment,
                ctx.buildInfoProcessingType
              )
            )
          }
          .getOrElse {
            // Currently we don't send notifications and don't add finished action into db if version id is missing.
            // It happens during cancel when there is no finished deploy action before it, but some scenario is running on engine.
            // TODO: We should send notifications and add action db entry in that cases as well as for normal finish.
            //       Before we can do that we should check if we somewhere rely on fact that version is always defined -
            //       see ProcessAction.processVersionId
            logger.info(s"Action $actionString finished for action without version id - skipping listener notification")
            removeInvalidAction(ctx.actionId)
          }
          .map(_ => result)
    }
  }

  private def removeInvalidAction(actionId: ProcessActionId): Future[Unit] = {
    dbioRunner.runInTransaction(actionRepository.removeAction(actionId))
  }

  // TODO: check deployment id to be sure that returned status is for given deployment
  override def getProcessState(
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser, ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState] = {
    dbioRunner.run(for {
      processDetailsOpt     <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id)
      processDetails        <- existsOrFail(processDetailsOpt, ProcessNotFoundError(processIdWithName.name))
      inProgressActionNames <- actionRepository.getInProgressActionNames(processDetails.processId)
      result                <- getProcessState(processDetails, inProgressActionNames)
    } yield result)
  }

  override def getProcessState(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit user: LoggedUser, ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState] = {
    dbioRunner.run(for {
      inProgressActionNames <- actionRepository.getInProgressActionNames(processDetails.processId)
      result                <- getProcessState(processDetails, inProgressActionNames)
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
  )(implicit ec: ExecutionContext): DB[Map[ProcessId, Set[ScenarioActionName]]] = {
    processTraverse.toList match {
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

  private def getProcessState(
      processDetails: ScenarioWithDetailsEntity[_],
      inProgressActionNames: Set[ScenarioActionName]
  )(implicit ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy, user: LoggedUser): DB[ProcessState] = {
    dispatcher
      .deploymentManager(processDetails.processingType)
      .map { manager =>
        if (processDetails.isFragment) {
          throw new FragmentStateException
        } else if (processDetails.isArchived) {
          getArchivedProcessState(processDetails)(manager)
        } else if (inProgressActionNames.contains(ScenarioActionName.Deploy)) {
          logger.debug(s"Status for: '${processDetails.name}' is: ${SimpleStateStatus.DuringDeploy}")
          DBIOAction.successful(
            manager.processStateDefinitionManager.processState(StatusDetails(SimpleStateStatus.DuringDeploy, None))
          )
        } else if (inProgressActionNames.contains(ScenarioActionName.Cancel)) {
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
                        .map(_.actionName)})"
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
    processDetails.lastStateAction.map(a => (a.actionName, a.state)) match {
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
          Some(ScenarioActionName.StateActions)
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
    WithDataFreshnessStatus.fresh(SimpleProcessStateDefinitionManager.ErrorFailedToGet)

  // It is very naive implementation for situation when designer was restarted after spawning some long running action
  // like deploy but before marking it as finished. Without this, user will always see "during deploy" status - even
  // if scenario was finished.
  // This implementation won't work correctly for >1 designer and rolling updates. Correct implementation should invalidate
  // only actions that were spawned by inactive designer or we should return running status even if local state
  // is "during deploy" or we should periodically synchronize local state with remote state and replace local state with the remote one.
  override def invalidateInProgressActions(): Unit = {
    Await.result(dbioRunner.run(actionRepository.deleteInProgressActions()), 10 seconds)
  }

  // TODO: further changes
  //       - block two concurrent custom actions - see ManagementResourcesConcurrentSpec
  //       - see those actions in the actions table
  //       - send notifications about finished/failed custom actions
  override def invokeCustomAction(
      actionName: ScenarioActionName,
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
        _ = checkIfCanPerformActionOnScenario(actionName, processDetails)
        processState <- DBIOAction.from(getProcessState(processDetails))
        manager = dispatcher.deploymentManagerUnsafe(processDetails.processingType)
        // Fetch and validate custom action details
        actionCommand = CustomActionCommand(
          actionName,
          processDetails.toEngineProcessVersion,
          processDetails.json,
          loggedUser.toManagerUser,
          params
        )
        customActionOpt = manager.customActionsDefinitions.find(_.actionName == actionName)
        _ <- existsOrFail(customActionOpt, CustomActionNonExisting(actionName))
        _ = checkIfCanPerformActionInState(actionName, processDetails, processState)
        invokeActionResult <- DBIOAction.from(manager.processCommand(actionCommand))
      } yield invokeActionResult
    )
  }

}

private class FragmentStateException extends BadRequestError("Fragment doesn't have state.")

private case class CustomActionNonExisting(actionName: ScenarioActionName)
    extends NotFoundError(s"$actionName is not existing")
case class ValidationError(message: String) extends BadRequestError(message)
