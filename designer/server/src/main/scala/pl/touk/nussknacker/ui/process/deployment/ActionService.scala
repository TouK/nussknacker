package pl.touk.nussknacker.ui.process.deployment

import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.Comment
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioStatusDto
import pl.touk.nussknacker.ui.api.{DeploymentCommentSettings, ListenerApiUser}
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnActionExecutionFinished, OnActionFailed, OnActionSuccess}
import pl.touk.nussknacker.ui.listener.{ProcessChangeListener, User => ListenerUser}
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.{AdminUser, LoggedUser, NussknackerInternalUser}
import slick.dbio.DBIOAction

import java.time.Clock
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

// Responsibility of this class is to wrap deployment actions with persistent, transactional context.
// It ensures that all actions are done consistently: do validations and ensures that only allowed actions
// will be executed in given state. It sends notifications about finished actions.
// Also thanks to it we are able to check if state on remote engine is the same as persisted state.
class ActionService(
    dispatcher: DeploymentManagerDispatcher,
    processRepository: FetchingProcessRepository[DB],
    actionRepository: ScenarioActionRepository,
    dbioRunner: DBIOActionRunner,
    processChangeListener: ProcessChangeListener,
    scenarioStateProvider: ScenarioStateProvider,
    deploymentCommentSettings: Option[DeploymentCommentSettings],
    modelInfos: ProcessingTypeDataProvider[ModelInfo, _],
    clock: Clock
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  def markActionExecutionFinished(
      processingType: ProcessingType,
      actionId: ProcessActionId
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

  def getLastStateAction(
      expectedProcessingType: ProcessingType,
      processId: ProcessId
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

  // It is very naive implementation for situation when designer was restarted after spawning some long running action
  // like deploy but before marking it as finished. Without this, user will always see "during deploy" status - even
  // if scenario was finished.
  // This implementation won't work correctly for >1 designer and rolling updates. Correct implementation should invalidate
  // only actions that were spawned by inactive designer or we should return running status even if local state
  // is "during deploy" or we should periodically synchronize local state with remote state and replace local state with the remote one.
  def invalidateInProgressActions(): Unit = {
    Await.result(dbioRunner.run(actionRepository.deleteInProgressActions()), 10.seconds)
  }

  def actionProcessorForLatestVersion[LatestScenarioDetailsShape: ScenarioShapeFetchStrategy]
      : ActionProcessor[LatestScenarioDetailsShape] =
    actionProcessorForVersion[LatestScenarioDetailsShape](p => Some(p.processVersionId))

  def actionProcessorForVersion[LatestScenarioDetailsShape: ScenarioShapeFetchStrategy](
      extractVersionOnWhichActionIsDoneFromLatestScenarioDetails: ScenarioWithDetailsEntity[
        LatestScenarioDetailsShape
      ] => Option[VersionId]
  ): ActionProcessor[LatestScenarioDetailsShape] =
    new ActionProcessor(extractVersionOnWhichActionIsDoneFromLatestScenarioDetails, _ => None)

  private def doMarkActionExecutionFinished(action: ProcessAction, expectedProcessingType: ProcessingType) = {
    for {
      _            <- validateExpectedProcessingType(expectedProcessingType, action.processId)
      updateResult <- actionRepository.markFinishedActionAsExecutionFinished(action.id)
    } yield updateResult
  }

  private def validateExpectedProcessingType(expectedProcessingType: ProcessingType, processId: ProcessId): DB[Unit] = {
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

  class ActionProcessor[LatestScenarioDetailsShape: ScenarioShapeFetchStrategy](
      extractVersionOnWhichActionIsDoneFromLatestScenarioDetails: ScenarioWithDetailsEntity[
        LatestScenarioDetailsShape
      ] => Option[VersionId],
      extractModelInfoFromLatestScenarioDetails: ScenarioWithDetailsEntity[
        LatestScenarioDetailsShape
      ] => Option[ModelInfo]
  ) {

    def withModelInfoSaving(implicit user: LoggedUser) =
      new ActionProcessor[LatestScenarioDetailsShape](
        extractVersionOnWhichActionIsDoneFromLatestScenarioDetails,
        p => Some(modelInfos.forProcessingTypeUnsafe(p.processingType))
      )

    def processAction[COMMAND <: ScenarioCommand[RESULT], RESULT](
        command: COMMAND,
        actionName: ScenarioActionName,
        dmCommandCreator: CommandContext[LatestScenarioDetailsShape] => DMScenarioCommand[RESULT],
    ): Future[RESULT] = {
      import command.commonData._
      processActionWithCustomFinalization[COMMAND, RESULT](command, actionName) { case (ctx, actionFinalizer) =>
        val dmCommand = dmCommandCreator(ctx)
        actionFinalizer.handleResult {
          dispatcher
            .deploymentManagerUnsafe(ctx.latestScenarioDetails.processingType)
            .processCommand(dmCommand)
        }
      }
    }

    def processActionWithCustomFinalization[COMMAND <: ScenarioCommand[RESULT], RESULT](
        command: COMMAND,
        actionName: ScenarioActionName
    )(runAction: (CommandContext[LatestScenarioDetailsShape], ActionFinalizer) => Future[RESULT]): Future[RESULT] = {
      import command.commonData._
      for {
        validatedComment <- validateDeploymentComment(comment)
        ctx <- prepareCommandContextWithAction(
          processIdWithName,
          actionName,
          extractVersionOnWhichActionIsDoneFromLatestScenarioDetails,
        )
        modelInfo = extractModelInfoFromLatestScenarioDetails(ctx.latestScenarioDetails)
        actionResult <- runAction(ctx, new ActionFinalizer(actionName, validatedComment, ctx, modelInfo))
      } yield actionResult
    }

    private def validateDeploymentComment(comment: Option[Comment]): Future[Option[Comment]] =
      Future.fromTry(DeploymentComment.createDeploymentComment(comment, deploymentCommentSettings).toEither.toTry)

    /**
     * Common validations and operations for a command execution.
     *
     * @return gathered data for further command execution
     */
    private def prepareCommandContextWithAction(
        processId: ProcessIdWithName,
        actionName: ScenarioActionName,
        extractVersionOnWhichActionIsDoneFromLatestScenarioDetails: ScenarioWithDetailsEntity[
          LatestScenarioDetailsShape
        ] => Option[VersionId]
    )(implicit user: LoggedUser): Future[CommandContext[LatestScenarioDetailsShape]] = {
      implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
      // 1.1 lock for critical section
      transactionallyRunCriticalSection(
        for {
          // 1.2. fetch scenario data
          processDetailsOpt <- processRepository.fetchLatestProcessDetailsForProcessId[LatestScenarioDetailsShape](
            processId.id
          )
          processDetails <- existsOrFail(processDetailsOpt, ProcessNotFoundError(processId.name))
          // 1.3. check if action is performed on proper scenario (not fragment, not archived)
          _ = checkIfCanPerformActionOnScenario(actionName, processDetails)
          // 1.4. check if action is allowed for current state
          processState <- scenarioStateProvider.getProcessStateDBIO(
            processDetails = processDetails,
            currentlyPresentedVersionId = None
          )
          _ = checkIfCanPerformActionInState(actionName, processDetails, processState)
          // 1.5. calculate which scenario version is affected by the action: latest for deploy, deployed for cancel
          versionOnWhichActionIsDone = extractVersionOnWhichActionIsDoneFromLatestScenarioDetails(processDetails)
          modelInfo                  = extractModelInfoFromLatestScenarioDetails(processDetails)
          // 1.6. create new action, action is started with "in progress" state, the whole command execution can take some time
          actionId <- actionRepository.addInProgressAction(
            processDetails.processId,
            actionName,
            versionOnWhichActionIsDone,
            modelInfo
          )
        } yield CommandContext(processDetails, actionId, versionOnWhichActionIsDone)
      )
    }

    private def transactionallyRunCriticalSection[T](dbioAction: DB[T]) = {
      dbioRunner.runInTransaction(actionRepository.withLockedTable(dbioAction))
    }

    private def checkIfCanPerformActionOnScenario(
        actionName: ScenarioActionName,
        processDetails: ScenarioWithDetailsEntity[LatestScenarioDetailsShape]
    ): Unit = {
      if (processDetails.isArchived) {
        throw ProcessIllegalAction.archived(actionName, processDetails.name)
      } else if (processDetails.isFragment) {
        throw ProcessIllegalAction.fragment(actionName, processDetails.name)
      }
    }

    private def checkIfCanPerformActionInState(
        actionName: ScenarioActionName,
        processDetails: ScenarioWithDetailsEntity[LatestScenarioDetailsShape],
        ps: ScenarioStatusDto
    ): Unit = {
      val allowedActions = ps.allowedActions.toSet
      if (!allowedActions.contains(actionName)) {
        logger.debug(s"Action: $actionName on process: ${processDetails.name} not allowed in ${ps.status} state")
        throw ProcessIllegalAction(actionName, processDetails.name, ps.status.name, allowedActions)
      }
    }

    private class ActionFinalizer(
        actionName: ScenarioActionName,
        deploymentComment: Option[Comment],
        ctx: CommandContext[LatestScenarioDetailsShape],
        modelInfo: Option[ModelInfo]
    )(implicit user: LoggedUser) {

      def handleResult[T](runAction: => Future[T]): Future[T] = {
        implicit val listenerUser: ListenerUser = ListenerApiUser(user)
        val actionFuture                        = runAction
        val actionString =
          s"${actionName.toString.toLowerCase} (id: ${ctx.actionId.value}) of ${ctx.latestScenarioDetails.name}"
        actionFuture.transformWith {
          case Failure(exception) =>
            logger.error(s"Action: $actionString finished with failure", exception)
            val performedAt = clock.instant()
            processChangeListener.handle(OnActionFailed(ctx.latestScenarioDetails.processId, exception, actionName))
            dbioRunner
              .runInTransaction(
                actionRepository.markActionAsFailed(
                  ctx.actionId,
                  ctx.latestScenarioDetails.processId,
                  actionName,
                  ctx.versionOnWhichActionIsDone,
                  performedAt,
                  deploymentComment,
                  exception.getMessage,
                  modelInfo
                )
              )
              .transform(_ => Failure(exception))
          case Success(result) =>
            ctx.versionOnWhichActionIsDone
              .map { versionOnWhichActionIsDone =>
                logger.info(s"Finishing $actionString")
                val performedAt = clock.instant()
                processChangeListener.handle(
                  OnActionSuccess(
                    ctx.latestScenarioDetails.processId,
                    versionOnWhichActionIsDone,
                    deploymentComment,
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
                    deploymentComment,
                    modelInfo
                  )
                )
              }
              .getOrElse {
                // Currently we don't send notifications and don't add finished action into db if version id is missing.
                // It happens during cancel when there is no finished deploy action before it, but some scenario is running on engine.
                // TODO: We should send notifications and add action db entry in that cases as well as for normal finish.
                //       Before we can do that we should check if we somewhere rely on fact that version is always defined -
                //       see ProcessAction.processVersionId
                logger.info(
                  s"Action $actionString finished for action without version id - skipping listener notification"
                )
                removeInvalidAction()
              }
              .map(_ => result)
        }
      }

      def removeInvalidAction(): Future[Unit] = {
        dbioRunner.runInTransaction(
          actionRepository.removeAction(
            ctx.actionId,
            ctx.latestScenarioDetails.processId,
            ctx.versionOnWhichActionIsDone
          )
        )
      }

    }

    private def existsOrFail[T](checkThisOpt: Option[T], failWith: => Exception): DB[T] = {
      checkThisOpt match {
        case Some(checked) => DBIOAction.successful(checked)
        case None          => DBIOAction.failed(failWith)
      }
    }

  }

}

case class CommandContext[PS: ScenarioShapeFetchStrategy](
    latestScenarioDetails: ScenarioWithDetailsEntity[PS],
    actionId: ProcessActionId,
    versionOnWhichActionIsDone: Option[VersionId]
)
