package pl.touk.nussknacker.ui.process.deployment

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
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

/**
  * This service should be responsible for wrapping deploying and cancelling task in persistent context.
  * The purpose of it is not to handle any other things from ManagementActor - see comments there
  */
class DeploymentService(processRepository: FetchingProcessRepository[Future],
                        actionRepository: DbProcessActionRepository,
                        subprocessResolver: SubprocessResolver)(implicit val ec: ExecutionContext) {

  def cancelProcess(processId: ProcessIdWithName, comment: Option[String],
                   performCancel: ProcessIdWithName => Future[Unit])(implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    for {
      _ <- performCancel(processId)
      maybeVersion <- findDeployedVersion(processId)
      version <- processDataExistOrFail(maybeVersion, processId.name.value)
      result <- actionRepository.markProcessAsCancelled(processId.id, version.value, comment)
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
        val deployedScenarioDataTry = resolveGraph(details.json.get).flatMap(canonical => toTry(ProcessCanonizer.uncanonize(canonical))).map { resolvedScenario =>
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
                                   performDeploy: (ProcessingType, ProcessVersion, DeploymentData, GraphProcess, Option[String]) => Future[_])(implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    for {
      resolvedGraphProcess <- Future.fromTry(resolveGraphProcess(latestVersion))
      maybeProcessName <- processRepository.fetchProcessName(ProcessId(latestVersion.processId))
      processName = maybeProcessName.getOrElse(throw new IllegalArgumentException(s"Unknown scenario Id ${latestVersion.processId}"))
      processVersion = latestVersion.toProcessVersion(processName)
      deploymentData = prepareDeploymentData(toManagerUser(user))
      _ <- performDeploy(processingType, processVersion, deploymentData, resolvedGraphProcess, savepointPath)
      deployedActionData <- actionRepository.markProcessAsDeployed(
        ProcessId(latestVersion.processId), latestVersion.id, processingType, comment
      )
    } yield deployedActionData
  }

  private def prepareDeploymentData(user: User) = {
    DeploymentData(DeploymentId(""), user, Map.empty)
  }

  private def resolveGraphProcess(processVersion: ProcessVersionEntityData): Try[GraphProcess] =
    resolveGraph(processVersion.toGraphProcess.jsonString).map(GraphProcess(_))

  // TODO: remove this code duplication with ManagementActor
  private def resolveGraph(canonicalJson: String): Try[String] = {
    toTry(ProcessMarshaller.fromJson(canonicalJson).toValidatedNel)
      .flatMap(resolveGraph)
      .map(ProcessMarshaller.toJson(_).noSpaces)
  }

  private def resolveGraph(canonical: CanonicalProcess): Try[CanonicalProcess] = {
    toTry(subprocessResolver.resolveSubprocesses(canonical.withoutDisabledNodes))
  }

  private def toTry[E, A](validated: ValidatedNel[E, A]) =
    validated.map(Success(_)).valueOr(e => Failure(new RuntimeException(e.head.toString)))

  private def findDeployedVersion(processId: ProcessIdWithName)(implicit user: LoggedUser): Future[Option[VersionId]] = for {
    process <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id)
    lastAction = process.flatMap(_.lastDeployedAction)
  } yield lastAction.map(la => la.processVersionId)

  private def toManagerUser(loggedUser: LoggedUser) = User(loggedUser.id, loggedUser.username)

}
