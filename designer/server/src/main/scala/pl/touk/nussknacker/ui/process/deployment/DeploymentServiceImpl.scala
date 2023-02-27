package pl.touk.nussknacker.ui.process.deployment

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, User}
import pl.touk.nussknacker.restmodel.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.api.ListenerApiUser
import pl.touk.nussknacker.ui.db.entity.ProcessActionEntityData
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnDeployActionFailed, OnDeployActionSuccess, OnFinished}
import pl.touk.nussknacker.ui.listener.{ProcessChangeListener, User => ListenerUser}
import pl.touk.nussknacker.ui.process.deployment.DeploymentActionType.{Cancel, Deployment}
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{DbProcessActionRepository, DeploymentComment, FetchingProcessRepository}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class DeploymentServiceImpl(dispatcher: DeploymentManagerDispatcher,
                            processRepository: FetchingProcessRepository[Future],
                            actionRepository: DbProcessActionRepository,
                            scenarioResolver: ScenarioResolver,
                            deploymentActionsInProgressRepository: DeploymentActionsInProgressRepository,
                            processChangeListener: ProcessChangeListener) extends DeploymentService with LazyLogging {

  override def cancelProcess(processId: ProcessIdWithName, deploymentComment: Option[DeploymentComment])
                            (implicit user: LoggedUser, ec: ExecutionContext): Future[_] = {
    handleDeploymentAction(processId, Cancel) {
      withDeploymentActionNotificationOpt(processId, "cancel", deploymentComment) {
        for {
          processingType <- processRepository.fetchProcessingType(processId.id)
          _ <- dispatcher.deploymentManager(processingType).cancel(processId.name, user.toManagerUser)
          maybeVersion <- findDeployedVersion(processId)
          // process can be have deployment in progress (no last deploy action) or be already cancelled - in this
          // situation we don't have version to mark as cancel
          result <- maybeVersion.map { version =>
            actionRepository.markProcessAsCancelled(processId.id, version, deploymentComment)
          }.sequence
        } yield result
      }
    }
  }

  def getDeployedScenarios(processingType: ProcessingType)
                          (implicit ec: ExecutionContext): Future[List[DeployedScenarioData]] = {
    for {
      deployedProcesses <- {
        implicit val userFetchingDataFromRepository: LoggedUser = NussknackerInternalUser
        processRepository.fetchProcessesDetails[CanonicalProcess](FetchProcessesDetailsQuery(isSubprocess = Some(false), isArchived = Some(false), isDeployed = Some(true), processingTypes = Some(Seq(processingType))))
      }
      dataList <- Future.sequence(deployedProcesses.flatMap { details =>
        val lastDeployAction = details.lastDeployedAction.get
        // TODO: is it created correctly? how to not create all this instances from scratch for different usages of deployment (by process.id or full process details)
        val processVersion = ProcessVersion(lastDeployAction.processVersionId, ProcessName(details.name), details.processId, details.createdBy, details.modelVersion)
        // TODO: what should be in name?
        val deployingUser = User(lastDeployAction.user, lastDeployAction.user)
        val deploymentData = prepareDeploymentData(deployingUser)
        val deployedScenarioDataTry = scenarioResolver.resolveScenario(details.json, details.processCategory).map { resolvedScenario =>
          DeployedScenarioData(processVersion, deploymentData, resolvedScenario)
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

  //inner Future in result allows to wait for deployment finish, while outer handles validation
  override def deployProcessAsync(processIdWithName: ProcessIdWithName,
                                  savepointPath: Option[String],
                                  deploymentComment: Option[DeploymentComment])
                                 (implicit user: LoggedUser, ec: ExecutionContext): Future[Future[_]] = {
    val deployAsyncFuture = for {
      maybeProcess <- processRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess](processIdWithName.id)
      process <- processDataExistOrFail(maybeProcess, processIdWithName.id)
      deploymentManager = dispatcher.deploymentManager(process.processingType)
      result <- deployAndSaveProcess(process, savepointPath, deploymentComment, deploymentManager)
    } yield result
    handleDeploymentAction(processIdWithName, Deployment)(deployAsyncFuture.flatten)
    deployAsyncFuture
  }

  private def handleDeploymentAction[T](id: ProcessIdWithName, actionType: DeploymentActionType)(actionFuture: Future[T])
                                       (implicit user: LoggedUser, ec: ExecutionContext): Future[T] = {
    for {
      actionId <- deploymentActionsInProgressRepository.addDeploymentActionInProgress(id, actionType)
      _ = {
        // TODO: maybe we should do it less asynchronously, in the same flow as notification saving?
        actionFuture.onComplete { _ =>
          deploymentActionsInProgressRepository.removedDeploymentActionInProgress(id, actionId)
        }
      }
      actionResult <- actionFuture
    } yield actionResult
  }

  private def processDataExistOrFail[T](maybeProcess: Option[T], processId: ProcessId): Future[T] = {
    maybeProcess match {
      case Some(processData) => Future.successful(processData)
      case None => Future.failed(ProcessNotFoundError(processId.value.toString))
    }
  }

  private def deployAndSaveProcess(process: BaseProcessDetails[CanonicalProcess],
                                   savepointPath: Option[String],
                                   deploymentComment: Option[DeploymentComment],
                                   deploymentManager: DeploymentManager)
                                  (implicit user: LoggedUser, ec: ExecutionContext): Future[Future[_]] = {
    val processVersion = process.toEngineProcessVersion
    val validatedData = for {
      resolvedCanonicalProcess <- Future.fromTry(scenarioResolver.resolveScenario(process.json, process.processCategory))
      deploymentData = prepareDeploymentData(user.toManagerUser)
      _ <- deploymentManager.validate(processVersion, deploymentData, resolvedCanonicalProcess)
    } yield (resolvedCanonicalProcess, deploymentData)

    validatedData.map { case (resolvedCanonicalProcess, deploymentData) =>
      //we notify of deployment finish/fail only if initial validation succeeded
      withDeploymentActionNotificationOpt(process.idWithName, "deploy", deploymentComment) {
        for {
          _ <- deploymentManager.deploy(processVersion, deploymentData, resolvedCanonicalProcess, savepointPath)
          deployedActionData <- actionRepository.markProcessAsDeployed(
            process.processId, process.processVersionId, process.processingType, deploymentComment)
        } yield Some(deployedActionData)
      }
    }
  }

  private def prepareDeploymentData(user: User) = {
    DeploymentData(DeploymentId(""), user, Map.empty)
  }

  private def findDeployedVersion(processId: ProcessIdWithName)
                                 (implicit user: LoggedUser, ec: ExecutionContext): Future[Option[VersionId]] = for {
    process <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id)
    lastAction = process.flatMap(_.lastDeployedAction)
  } yield lastAction.map(la => la.processVersionId)

  private def withDeploymentActionNotificationOpt(processIdWithName: ProcessIdWithName,
                                                  actionName: String,
                                                  deploymentComment: Option[DeploymentComment])(action: => Future[Option[ProcessActionEntityData]])
                                                 (implicit user: LoggedUser, ec: ExecutionContext): Future[Option[ProcessActionEntityData]] = {
    implicit val listenerUser: ListenerUser = ListenerApiUser(user)
    val actionToRun = action
    actionToRun.onComplete {
      case Failure(failure) =>
        logger.error(s"Action: $actionName of ${processIdWithName.name} finished with failure", failure)
        processChangeListener.handle(OnDeployActionFailed(processIdWithName.id, failure))
      case Success(Some(details)) =>
        logger.info(s"Finishing $actionName of ${processIdWithName.name}")
        processChangeListener.handle(OnDeployActionSuccess(details.processId, details.processVersionId, deploymentComment, details.performedAtTime, details.action))
      case Success(None) =>
        logger.info(s"Action $actionName of ${processIdWithName.name} finished without any effect - skipping listener notification")
    }
    actionToRun
  }

  override def getProcessState(processIdWithName: ProcessIdWithName)
                              (implicit user: LoggedUser, ec: ExecutionContext): Future[ProcessState] = {
    for {
      manager <- dispatcher.deploymentManager(processIdWithName.id)
      inProgressActionTypes <- deploymentActionsInProgressRepository.getDeploymentActionInProgressTypes(processIdWithName)
      result <- {
        if (inProgressActionTypes.contains(Deployment)) {
          Future.successful(manager.processStateDefinitionManager.processState(SimpleStateStatus.DuringDeploy))
        } else if (inProgressActionTypes.contains(Cancel)) {
          Future.successful(manager.processStateDefinitionManager.processState(SimpleStateStatus.DuringCancel))
        } else {
          for {
            actions <- processRepository.fetchProcessActions(processIdWithName.id)
            state <- findJobState(manager, processIdWithName)
            _ <- handleFinishedProcess(processIdWithName, state)
          } yield ObsoleteStateDetector.handleObsoleteStatus(state, actions.headOption)
        }
      }
    } yield result
  }

  private def findJobState(deploymentManager: DeploymentManager, processIdWithName: ProcessIdWithName)
                          (implicit user: LoggedUser, ec: ExecutionContext): Future[Option[ProcessState]] =
    deploymentManager.findJobStatus(processIdWithName.name).recover {
      case NonFatal(e) =>
        logger.warn(s"Failed to get status of ${processIdWithName}: ${e.getMessage}", e)
        Some(SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.FailedToGet))
    }

  //TODO: there is small problem here: if no one invokes process status for long time, Flink can remove process from history
  //- then it's gone, not finished.
  private def handleFinishedProcess(idWithName: ProcessIdWithName, processState: Option[ProcessState])
                                   (implicit ec: ExecutionContext): Future[Unit] = {
    implicit val user: NussknackerInternalUser.type = NussknackerInternalUser
    implicit val listenerUser: ListenerUser = ListenerApiUser(user)
    processState match {
      case Some(state) if state.status.isFinished =>
        findDeployedVersion(idWithName).flatMap {
          case Some(version) => {
            val finishedDeploymentComment = DeploymentComment.unsafe("Scenario finished")
            actionRepository.markProcessAsCancelled(idWithName.id, version, Some(finishedDeploymentComment)).map(_ =>
              processChangeListener.handle(OnFinished(idWithName.id, version))
            )
          }
          case _ => Future.successful(())
        }
      case _ => Future.successful(())
    }
  }

}
