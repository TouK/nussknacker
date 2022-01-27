package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.db.entity.ProcessActionEntityData
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{DbProcessActionRepository, FetchingProcessRepository}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * This service should be responsible for wrapping deploying and cancelling task in persistent context.
  * The purpose of it is not to handle any other things from ManagementActor - see comments there
  */
class DeploymentService(processRepository: FetchingProcessRepository[Future],
                        actionRepository: DbProcessActionRepository,
                        graphProcessResolver: GraphProcessResolver)(implicit val ec: ExecutionContext) {

  def cancelProcess(processId: ProcessIdWithName, comment: Option[String],
                   performCancel: ProcessIdWithName => Future[Unit])(implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    for {
      _ <- performCancel(processId)
      maybeVersion <- findDeployedVersion(processId)
      version <- processDataExistOrFail(maybeVersion, processId.name.value)
      result <- actionRepository.markProcessAsCancelled(processId.id, version, comment)
    } yield result
  }

  def getDeployedScenarios(processingType: ProcessingType): Future[List[DeployedScenarioData]] = {
    for {
      deployedProcesses <- {
        implicit val userFetchingDataFromRepository: LoggedUser = NussknackerInternalUser
        processRepository.fetchProcesses[CanonicalProcess](Some(false), Some(false), isDeployed = Some(true), None, Some(Seq(processingType)))
      }
      dataList <- Future.sequence(deployedProcesses.map { details =>
        val lastDeployAction = details.lastDeployedAction.get
        // TODO: is it created correctly? how to not create all this instances from scratch for different usages of deployment (by process.id or full process details)
        val processVersion = ProcessVersion(lastDeployAction.processVersionId, ProcessName(details.name), details.processId, details.createdBy, details.modelVersion)
        // TODO: what should be in name?
        val deployingUser = User(lastDeployAction.user, lastDeployAction.user)
        val deploymentData = prepareDeploymentData(deployingUser)
        val deployedScenarioDataTry = graphProcessResolver.resolveCanonicalProcess(details.json).flatMap(canonical => {
          ProcessCanonizer.uncanonize(canonical).map(Success(_)).valueOr(e => Failure(new RuntimeException(e.head.toString)))
        }).map { resolvedScenario =>
          DeployedScenarioData(processVersion, deploymentData, resolvedScenario)
        }
        Future.fromTry(deployedScenarioDataTry)
      })
    } yield dataList
  }

  def deployProcess(processId: ProcessId, savepointPath: Option[String], comment: Option[String],
                    performDeploy: (ProcessingType, ProcessVersion, DeploymentData, GraphProcess, Option[String]) => Future[_])
                   (implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    for {
      maybeProcess <- processRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess](processId)
      process <- processDataExistOrFail(maybeProcess, processId.value.toString)
      result <- deployAndSaveProcess(process, savepointPath, comment, performDeploy)
    } yield result
  }

  private def processDataExistOrFail[T](maybeProcess: Option[T], processId: String): Future[T] = {
    maybeProcess match {
      case Some(processData) => Future.successful(processData)
      case None => Future.failed(ProcessNotFoundError(processId))
    }
  }

  private def deployAndSaveProcess(process: BaseProcessDetails[CanonicalProcess],
                                   savepointPath: Option[String],
                                   comment: Option[String],
                                   performDeploy: (ProcessingType, ProcessVersion, DeploymentData, GraphProcess, Option[String]) => Future[_])(implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    for {
      resolvedGraphProcess <- Future.fromTry(graphProcessResolver.resolveGraphProcess(process.json))
      processVersion = process.toEngineProcessVersion
      deploymentData = prepareDeploymentData(toManagerUser(user))
      _ <- performDeploy(process.processingType, processVersion, deploymentData, resolvedGraphProcess, savepointPath)
      deployedActionData <- actionRepository.markProcessAsDeployed(
        process.processId, process.processVersionId, process.processingType, comment
      )
    } yield deployedActionData
  }

  private def prepareDeploymentData(user: User) = {
    DeploymentData(DeploymentId(""), user, Map.empty)
  }

  private def findDeployedVersion(processId: ProcessIdWithName)(implicit user: LoggedUser): Future[Option[VersionId]] = for {
    process <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id)
    lastAction = process.flatMap(_.lastDeployedAction)
  } yield lastAction.map(la => la.processVersionId)

  private def toManagerUser(loggedUser: LoggedUser) = User(loggedUser.id, loggedUser.username)

}
