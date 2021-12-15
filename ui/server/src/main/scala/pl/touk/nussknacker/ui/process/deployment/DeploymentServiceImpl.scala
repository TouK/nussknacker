package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.ui.db.entity.{ProcessActionEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{DbProcessActionRepository, FetchingProcessRepository}
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class DeploymentServiceImpl(processRepository: FetchingProcessRepository[Future],
                            actionRepository: DbProcessActionRepository,
                            subprocessResolver: SubprocessResolver)(implicit val ec: ExecutionContext) extends DeploymentService {

  def cancelProcess(processId: ProcessIdWithName, comment: Option[String],
                   performCancel: ProcessIdWithName => Future[Unit])(implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    for {
      _ <- performCancel(processId)
      maybeVersion <- findDeployedVersion(processId)
      version <- processDataExistOrFail(maybeVersion, processId.name.value)
      result <- actionRepository.markProcessAsCancelled(processId.id, version.value, comment)
    } yield result
  }

  override def getDeployedScenarios(processingType: String): Future[List[DeployedScenarioData]] = {
    for {
      deployedProcesses <- {
        implicit val userFetchingDataFromRepository: LoggedUser = NussknackerInternalUser
        processRepository.fetchProcesses[CanonicalProcess](Some(false), Some(false), isDeployed = Some(true), None, Some(Seq(processingType)))
      }
      dataList <- Future.sequence(deployedProcesses.map { details =>
        val lastDeployAction = details.lastDeployedAction.get
        // TODO: is it created correctly? how to not create all this instances from scratch for different usages of deployment (by process.id or full process details)
        val processVersion = ProcessVersion(lastDeployAction.processVersionId, ProcessName(details.name), ProcessId(details.id), details.createdBy, details.modelVersion)
        // TODO: what should be in name?
        val deployingUser = User(lastDeployAction.user, lastDeployAction.user)
        val deploymentData = prepareDeploymentData(deployingUser)
        val deployedScenarioDataTry = resolveGraph(details.json.get).map { resolvedGraph =>
          DeployedScenarioData(processVersion, deploymentData, GraphProcess(ProcessMarshaller.toJson(resolvedGraph).noSpaces))
        }
        Future.fromTry(deployedScenarioDataTry)
      })
    } yield dataList
  }

  def deployProcess(processId: ProcessId, savepointPath: Option[String], comment: Option[String],
                    performDeploy: (ProcessingType, ProcessVersion, DeploymentData, ProcessDeploymentData, Option[String]) => Future[_])
                   (implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    for {
      processingType <- processRepository.fetchProcessingType(processId)
      maybeLatestVersion <- processRepository.fetchLatestProcessVersion[DisplayableProcess](processId)
      latestVersion <- processDataExistOrFail(maybeLatestVersion, processId.value.toString)
      result <- deployAndSaveProcess(processingType, latestVersion, savepointPath, comment, performDeploy)
    } yield result
  }

  private def processDataExistOrFail[T](maybeProcessData: Option[T], processId: String): Future[T] = {
    maybeProcessData match {
      case Some(processData) => Future.successful(processData)
      case None => Future.failed(ProcessNotFoundError(processId))
    }
  }

  private def deployAndSaveProcess(processingType: ProcessingType,
                                   latestVersion: ProcessVersionEntityData,
                                   savepointPath: Option[String],
                                   comment: Option[String],
                                   performDeploy: (ProcessingType, ProcessVersion, DeploymentData, ProcessDeploymentData, Option[String]) => Future[_])(implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    for {
      resolvedDeploymentData <- Future.fromTry(resolveDeploymentData(latestVersion.deploymentData))
      maybeProcessName <- processRepository.fetchProcessName(ProcessId(latestVersion.processId))
      processName = maybeProcessName.getOrElse(throw new IllegalArgumentException(s"Unknown scenario Id ${latestVersion.processId}"))
      processVersion = latestVersion.toProcessVersion(processName)
      deploymentData = prepareDeploymentData(toManagerUser(user))
      _ <- performDeploy(processingType, processVersion, deploymentData, resolvedDeploymentData, savepointPath)
      deployedActionData <- actionRepository.markProcessAsDeployed(
        ProcessId(latestVersion.processId), latestVersion.id, processingType, comment
      )
    } yield deployedActionData
  }

  private def prepareDeploymentData(user: User) = {
    DeploymentData(DeploymentId(""), user, Map.empty)
  }

  private def resolveDeploymentData(data: ProcessDeploymentData): Try[ProcessDeploymentData] = data match {
    case GraphProcess(canonical) =>
      resolveGraph(canonical).map(GraphProcess)
    case a =>
      Success(a)
  }

  // TODO: remove this code duplication with ManagementActor

  private def resolveGraph(canonicalJson: String): Try[String] = {
    ProcessMarshaller.fromJson(canonicalJson)
      .map(resolveGraph)
      .valueOr(e => Failure(new RuntimeException(e.toString)))
      .map(ProcessMarshaller.toJson(_).noSpaces)
  }

  private def resolveGraph(canonical: CanonicalProcess): Try[CanonicalProcess] = {
    subprocessResolver
      .resolveSubprocesses(canonical.withoutDisabledNodes)
      .map(Success(_))
      .valueOr(e => Failure(new RuntimeException(e.head.toString)))
  }

  private def findDeployedVersion(processId: ProcessIdWithName)(implicit user: LoggedUser): Future[Option[VersionId]] = for {
    process <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id)
    lastAction = process.flatMap(_.lastDeployedAction)
  } yield lastAction.map(la => la.processVersionId)

  private def toManagerUser(loggedUser: LoggedUser) = User(loggedUser.id, loggedUser.username)

}
