package pl.touk.nussknacker.ui.process.deployment

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, User}
import pl.touk.nussknacker.restmodel.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.api.ListenerApiUser
import pl.touk.nussknacker.ui.db.entity.ProcessActionEntityData
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnDeployActionFailed, OnDeployActionSuccess, OnFinished}
import pl.touk.nussknacker.ui.listener.{ProcessChangeListener, User => ListenerUser}
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{DbProcessActionRepository, DeploymentComment, FetchingProcessRepository}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * This service should be responsible for wrapping deploying and cancelling task in persistent context.
 * The purpose of it is not to handle any other things from ManagementActor - see comments there
 */
class DeploymentService(getDeploymentManager: ProcessingType => DeploymentManager,
                        processRepository: FetchingProcessRepository[Future],
                        actionRepository: DbProcessActionRepository,
                        scenarioResolver: ScenarioResolver,
                        processChangeListener: ProcessChangeListener)(implicit val ec: ExecutionContext) extends LazyLogging {

  def cancelProcess(processId: ProcessIdWithName, deploymentComment: Option[DeploymentComment], performCancel: ProcessIdWithName => Future[Unit])(implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    withDeploymentActionNotification(processId, "cancel", deploymentComment) {
      for {
        _ <- performCancel(processId)
        maybeVersion <- findDeployedVersion(processId)
        version <- processDataExistOrFail(maybeVersion, processId.id)
        result <- actionRepository.markProcessAsCancelled(processId.id, version, deploymentComment)
      } yield result
    }
  }

  def getDeployedScenarios(processingType: ProcessingType): Future[List[DeployedScenarioData]] = {
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
  def deployProcess(processIdWithName: ProcessIdWithName,
                    savepointPath: Option[String],
                    deploymentComment: Option[DeploymentComment])
                   (implicit user: LoggedUser): Future[Future[ProcessActionEntityData]] = {
    for {
      maybeProcess <- processRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess](processIdWithName.id)
      process <- processDataExistOrFail(maybeProcess, processIdWithName.id)
      deploymentManager = getDeploymentManager(process.processingType)
      result <- deployAndSaveProcess(process, savepointPath, deploymentComment, deploymentManager)
    } yield result
  }

  //TODO: there is small problem here: if no one invokes process status for long time, Flink can remove process from history
  //- then it's gone, not finished.
  def handleFinishedProcess(idWithName: ProcessIdWithName, processState: Option[ProcessState]): Future[Unit] = {
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

  private def processDataExistOrFail[T](maybeProcess: Option[T], processId: ProcessId): Future[T] = {
    maybeProcess match {
      case Some(processData) => Future.successful(processData)
      case None => Future.failed(ProcessNotFoundError(processId.value.toString))
    }
  }

  private def deployAndSaveProcess(process: BaseProcessDetails[CanonicalProcess],
                                   savepointPath: Option[String],
                                   deploymentComment: Option[DeploymentComment],
                                   deploymentManager: DeploymentManager)(implicit user: LoggedUser): Future[Future[ProcessActionEntityData]] = {
    val processVersion = process.toEngineProcessVersion
    val validatedData = for {
      resolvedCanonicalProces <- Future.fromTry(scenarioResolver.resolveScenario(process.json, process.processCategory))
      deploymentData = prepareDeploymentData(toManagerUser(user))
      _ <- deploymentManager.validate(processVersion, deploymentData, resolvedCanonicalProces)
    } yield (resolvedCanonicalProces, deploymentData)

    validatedData.map { case (resolvedCanonicalProces, deploymentData) =>
      //we notify of deployment finish/fail only if initial validation succeeded
      withDeploymentActionNotification(process.idWithName, "deploy", deploymentComment) {
        for {
          _ <- deploymentManager.deploy(processVersion, deploymentData, resolvedCanonicalProces, savepointPath)
          deployedActionData <- actionRepository.markProcessAsDeployed(
            process.processId, process.processVersionId, process.processingType, deploymentComment)
        } yield deployedActionData
      }
    }
  }

  private def prepareDeploymentData(user: User) = {
    DeploymentData(DeploymentId(""), user, Map.empty)
  }

  private def findDeployedVersion(processId: ProcessIdWithName)(implicit user: LoggedUser): Future[Option[VersionId]] = for {
    process <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id)
    lastAction = process.flatMap(_.lastDeployedAction)
  } yield lastAction.map(la => la.processVersionId)

  private def toManagerUser(loggedUser: LoggedUser) = User(loggedUser.id, loggedUser.username)

  private def withDeploymentActionNotification(processIdWithName: ProcessIdWithName,
                                               actionName: String,
                                               deploymentComment: Option[DeploymentComment])(action: => Future[ProcessActionEntityData])(implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    implicit val listenerUser: ListenerUser = ListenerApiUser(user)
    val actionToRun = action
    actionToRun.onComplete {
      case Failure(failure) =>
        logger.error(s"Action: $actionName of ${processIdWithName.name} finished with failure", failure)
        processChangeListener.handle(OnDeployActionFailed(processIdWithName.id, failure))
      case Success(details) =>
        logger.info(s"Finishing $actionName of ${processIdWithName.name}")
        processChangeListener.handle(OnDeployActionSuccess(details.processId, details.processVersionId, deploymentComment, details.performedAtTime, details.action))
    }
    actionToRun
  }

}
