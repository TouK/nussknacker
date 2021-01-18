package pl.touk.nussknacker.engine.management

import java.io.File

import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName

import scala.concurrent.{ExecutionContext, Future}

abstract class FlinkProcessManager(modelData: ModelData, shouldVerifyBeforeDeploy: Boolean, mainClassName: String)
  extends ProcessManager with LazyLogging {

  protected lazy val jarFile: File = new FlinkModelJar().buildJobJar(modelData)

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private lazy val testRunner = new FlinkProcessTestRunner(modelData)

  private lazy val verification = new FlinkProcessVerifier(modelData)

  override def deploy(processVersion: ProcessVersion, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String], user: User): Future[Unit] = {
    val processName = processVersion.processName

    import cats.data.OptionT
    import cats.implicits._

    val stoppingResult = for {
      oldJob <- OptionT(findJobStatus(processName))
      deploymentId <- OptionT.fromOption[Future](oldJob.deploymentId)
      _ <- OptionT[Future, Unit](if (!oldJob.allowedActions.contains(ProcessActionType.Deploy))
        Future.failed(new IllegalStateException(s"Job ${processName.value} cannot be deployed, status: ${oldJob.status.name}")) else Future.successful(Some(())))
      //when it's failed we don't need savepoint...
      if oldJob.isDeployed
      maybeSavePoint <- OptionT.liftF(stopSavingSavepoint(processVersion, deploymentId, processDeploymentData))
    } yield {
      logger.info(s"Deploying $processName. Saving savepoint finished")
      maybeSavePoint
    }

    stoppingResult.value.flatMap { maybeSavepoint =>
      runProgram(processName,
        prepareProgramMainClass(processDeploymentData),
        prepareProgramArgs(processVersion, processDeploymentData),
        savepointPath.orElse(maybeSavepoint))
    }
  }

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

  override def cancel(processName: ProcessName, user: User): Future[Unit] = {
    findJobStatus(processName).flatMap {
      case Some(ProcessState(Some(deploymentId), _, _, actions, _, _, _, _, _, _)) if actions.contains(ProcessActionType.Cancel) =>
        cancel(deploymentId)
      case state =>
        logger.warn(s"Trying to cancel ${processName.value} which is not running but in status: $state")
        Future.successful(())
    }
  }

  override def customActions: List[CustomAction] = List.empty

  override def invokeCustomAction(actionRequest: CustomActionRequest,
                                  processDeploymentData: ProcessDeploymentData): Future[Either[CustomActionError, CustomActionResult]] =
    Future.successful(Left(CustomActionNotImplemented(actionRequest)))

  private def requireRunningProcess[T](processName: ProcessName)(action: DeploymentId => Future[T]): Future[T] = {
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

  private def checkIfJobIsCompatible(savepointPath: String, processDeploymentData: ProcessDeploymentData, processVersion: ProcessVersion): Future[Unit] =
    processDeploymentData match {
      case GraphProcess(processAsJson) if shouldVerifyBeforeDeploy =>
        verification.verify(processVersion, processAsJson, savepointPath)
      case _ => Future.successful(())
    }

  private def stopSavingSavepoint(processVersion: ProcessVersion, deploymentId: DeploymentId, processDeploymentData: ProcessDeploymentData): Future[String] = {
    logger.debug(s"Making savepoint of  ${processVersion.processName}. Deployment: $deploymentId")
    for {
      savepointResult <- makeSavepoint(deploymentId, savepointDir = None)
      savepointPath = savepointResult.path
      _ <- checkIfJobIsCompatible(savepointPath, processDeploymentData, processVersion)
      _ <- cancel(deploymentId)
    } yield savepointPath
  }

  private def prepareProgramArgs(processVersion: ProcessVersion, processDeploymentData: ProcessDeploymentData) : List[String] = {
    processDeploymentData match {
      case GraphProcess(processAsJson) =>
        List(processAsJson, toJsonString(processVersion), modelData.serializedConfigToPassInExecution)
      case CustomProcess(_) =>
        List(processVersion.processName.value, modelData.serializedConfigToPassInExecution)
    }
  }
  private def toJsonString(processVersion: ProcessVersion): String = {
    Encoder[ProcessVersion].apply(processVersion).spaces2
  }

  private def prepareProgramMainClass(processDeploymentData: ProcessDeploymentData) : String = {
    processDeploymentData match {
      case GraphProcess(_) => mainClassName
      case CustomProcess(mainClass) => mainClass
    }
  }

  protected def cancel(deploymentId: DeploymentId): Future[Unit]

  protected def makeSavepoint(deploymentId: DeploymentId, savepointDir: Option[String]): Future[SavepointResult]

  protected def stop(deploymentId: DeploymentId, savepointDir: Option[String]): Future[SavepointResult]

  protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Unit]

  override def processStateDefinitionManager: ProcessStateDefinitionManager = FlinkProcessStateDefinitionManager
}
