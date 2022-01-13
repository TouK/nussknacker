package pl.touk.nussknacker.engine.management

import cats.data.OptionT
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.FlinkDeploymentManager.prepareProgramArgs

import scala.concurrent.{ExecutionContext, Future}

abstract class FlinkDeploymentManager(modelData: ModelData, shouldVerifyBeforeDeploy: Boolean, mainClassName: String)(implicit ec: ExecutionContext)
  extends DeploymentManager with LazyLogging {

  private lazy val testRunner = new FlinkProcessTestRunner(modelData)

  private lazy val verification = new FlinkProcessVerifier(modelData)

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, graphProcess: GraphProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    val processName = processVersion.processName

    val stoppingResult = for {
      oldJob <- OptionT(findJobStatus(processName))
      deploymentId <- OptionT.fromOption[Future](oldJob.deploymentId)
      _ <- OptionT[Future, Unit](if (!oldJob.allowedActions.contains(ProcessActionType.Deploy))
        Future.failed(new IllegalStateException(s"Job ${processName.value} cannot be deployed, status: ${oldJob.status.name}")) else Future.successful(Some(())))
      //when it's failed we don't need savepoint...
      if oldJob.isDeployed
      _ <- OptionT(checkRequiredSlotsExceedAvailableSlots(graphProcess, Some(deploymentId)).map(Option(_)))
      maybeSavePoint <- OptionT.liftF(stopSavingSavepoint(processVersion, deploymentId, graphProcess))
    } yield {
      logger.info(s"Deploying $processName. Saving savepoint finished")
      maybeSavePoint
    }

    for {
      maybeSavepoint <- stoppingResult.value
      // In case of redeploy we double check required slots which is not bad because can be some run between jobs and it is better to check it again
      _ <- checkRequiredSlotsExceedAvailableSlots(graphProcess, None)
      runResult <- runProgram(
        processName,
        mainClassName,
        prepareProgramArgs(modelData.inputConfigDuringExecution.serialized, processVersion, deploymentData, graphProcess),
        savepointPath.orElse(maybeSavepoint)
      )
    } yield runResult
  }

  protected def checkRequiredSlotsExceedAvailableSlots(graphProcess: GraphProcess, currentlyDeployedJobId: Option[ExternalDeploymentId]): Future[Unit]

  override def savepoint(processName: ProcessName, savepointDir: Option[String]): Future[SavepointResult] = {
    requireRunningProcess(processName) {
      makeSavepoint(_, savepointDir)
    }
  }

  override def stop(processName: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] = {
    requireRunningProcess(processName) {
      stop(_, savepointDir)
    }
  }

  override def test[T](processName: ProcessName, processJson: String, testData: TestData, variableEncoder: Any => T): Future[TestResults[T]] = {
    testRunner.test(processName, processJson, testData, variableEncoder)
  }

  override def customActions: List[CustomAction] = List.empty

  override def invokeCustomAction(actionRequest: CustomActionRequest, graphProcess: GraphProcess): Future[Either[CustomActionError, CustomActionResult]] =
    Future.successful(Left(CustomActionNotImplemented(actionRequest)))

  private def requireRunningProcess[T](processName: ProcessName)(action: ExternalDeploymentId => Future[T]): Future[T] = {
    val name = processName.value
    findJobStatus(processName).flatMap {
      case Some(ProcessState(Some(deploymentId), status, _, _, _, _, _, _, _, _)) if status.isRunning =>
        action(deploymentId)
      case Some(state) =>
        Future.failed(new IllegalStateException(s"Job $name is not running, status: ${state.status.name}"))
      case None =>
        Future.failed(new IllegalStateException(s"Job $name not found"))
    }
  }

  private def checkIfJobIsCompatible(savepointPath: String, graphProcess: GraphProcess, processVersion: ProcessVersion): Future[Unit] =
    if (shouldVerifyBeforeDeploy)
      verification.verify(processVersion, graphProcess.jsonString, savepointPath)
    else Future.successful(())

  private def stopSavingSavepoint(processVersion: ProcessVersion, deploymentId: ExternalDeploymentId, graphProcess: GraphProcess): Future[String] = {
    logger.debug(s"Making savepoint of  ${processVersion.processName}. Deployment: $deploymentId")
    for {
      savepointResult <- makeSavepoint(deploymentId, savepointDir = None)
      savepointPath = savepointResult.path
      _ <- checkIfJobIsCompatible(savepointPath, graphProcess, processVersion)
      _ <- cancel(deploymentId)
    } yield savepointPath
  }

  protected def cancel(deploymentId: ExternalDeploymentId): Future[Unit]

  protected def makeSavepoint(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult]

  protected def stop(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult]

  protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Option[ExternalDeploymentId]]

  override def processStateDefinitionManager: ProcessStateDefinitionManager = FlinkProcessStateDefinitionManager
}

object FlinkDeploymentManager {

  def prepareProgramArgs(serializedConfig: String, processVersion: ProcessVersion, deploymentData: DeploymentData, graphProcess: GraphProcess) : List[String] =
    List(graphProcess.jsonString, processVersion.asJson.spaces2, deploymentData.asJson.spaces2, serializedConfig)

}
