package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.ui.db.entity.{ProcessActionEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{DbProcessActionRepository, FetchingProcessRepository}
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.CatsSyntax

import scala.concurrent.{ExecutionContext, Future}

class DeploymentServiceImpl(processRepository: FetchingProcessRepository[Future],
                            deployedProcessRepository: DbProcessActionRepository,
                            subprocessResolver: SubprocessResolver)(implicit val ec: ExecutionContext) {

  def cancelProcess(processId: ProcessIdWithName, comment: Option[String],
                   performCancel: ProcessIdWithName => Future[Unit])(implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    for {
      _ <- performCancel(processId)
      maybeVersion <- findDeployedVersion(processId)
      version <- processDataExistOrFail(maybeVersion, processId.name.value)
      result <- deployedProcessRepository.markProcessAsCancelled(processId.id, version.value, comment)
    } yield result
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
      resolvedDeploymentData <- resolveDeploymentData(latestVersion.deploymentData)
      maybeProcessName <- processRepository.fetchProcessName(ProcessId(latestVersion.processId))
      processName = maybeProcessName.getOrElse(throw new IllegalArgumentException(s"Unknown scenario Id ${latestVersion.processId}"))
      processVersion = latestVersion.toProcessVersion(processName)
      deploymentData = DeploymentData(DeploymentId(""), toManagerUser(user), Map.empty)
      _ <- performDeploy(processingType, processVersion, deploymentData, resolvedDeploymentData, savepointPath)
      deployedActionData <- deployedProcessRepository.markProcessAsDeployed(
        ProcessId(latestVersion.processId), latestVersion.id, processingType, comment
      )
    } yield deployedActionData
  }

  private def resolveDeploymentData(data: ProcessDeploymentData) = data match {
    case GraphProcess(canonical) =>
      resolveGraph(canonical).map(GraphProcess)
    case a =>
      Future.successful(a)
  }

  // TODO: remove this code duplication with ManagementActor

  private def resolveGraph(canonicalJson: String): Future[String] = {
    val validatedGraph = ProcessMarshaller.fromJson(canonicalJson)
      .map(_.withoutDisabledNodes)
      .toValidatedNel
      .andThen(subprocessResolver.resolveSubprocesses)
      .map(proc => ProcessMarshaller.toJson(proc).noSpaces)
    CatsSyntax.toFuture(validatedGraph)(e => new RuntimeException(e.head.toString))
  }

  private def findDeployedVersion(processId: ProcessIdWithName)(implicit user: LoggedUser): Future[Option[VersionId]] = for {
    process <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id)
    lastAction = process.flatMap(_.lastDeployedAction)
  } yield lastAction.map(la => la.processVersionId)

  private def toManagerUser(loggedUser: LoggedUser) = User(loggedUser.id, loggedUser.username)

}
