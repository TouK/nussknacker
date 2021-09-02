package pl.touk.nussknacker.engine.management

import cats.data.OptionT
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax.EncoderOps
import org.apache.flink.configuration.{Configuration, CoreOptions}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.management.FlinkDeploymentManager.{NotEnoughSlotsException, prepareProgramArgs}
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.ClusterOverview
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

abstract class FlinkDeploymentManager(modelData: ModelData, shouldVerifyBeforeDeploy: Boolean, mainClassName: String)
  extends DeploymentManager with LazyLogging {

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private lazy val testRunner = new FlinkProcessTestRunner(modelData)

  private lazy val verification = new FlinkProcessVerifier(modelData)

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    val processName = processVersion.processName

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

    for {
      maybeSavepoint <- stoppingResult.value
      _ <- checkExpectedSlotsExceedAvailableSlots(processDeploymentData)
      runResult <- {
        runProgram(processName,
          prepareProgramMainClass(processDeploymentData),
          prepareProgramArgs(modelData.inputConfigDuringExecution.serialized, processVersion, deploymentData, processDeploymentData),
          savepointPath.orElse(maybeSavepoint))
      }
    } yield runResult
  }

  private[management] def checkExpectedSlotsExceedAvailableSlots(processDeploymentData: ProcessDeploymentData): Future[Unit] = {
    val collectedSlotsCheckInputs = for {
      neededSlots <- determineNeededSlots(processDeploymentData)
      clusterOverview <- OptionT(getClusterOverview.map(Option(_)))
    } yield (neededSlots, clusterOverview)

    val checkResult = for {
      collectedInputs <- OptionT(collectedSlotsCheckInputs.value.recover {
        case NonFatal(ex) =>
          logger.warn("Error during collecting inputs needed for available slots checking. Slots checking will be omitted", ex)
          None
      })
      (neededSlots, clusterOverview) = collectedInputs
      _ <- OptionT(
        if (neededSlots > clusterOverview.`slots-available`)
          Future.failed(NotEnoughSlotsException(clusterOverview, neededSlots))
        else
          Future.successful(Option(())))
    } yield ()
    checkResult.value.map(_ => Unit)
  }

  private def determineNeededSlots(processDeploymentData: ProcessDeploymentData): OptionT[Future, Int] = {
    processDeploymentData match {
      case GraphProcess(processAsJson) =>
        val process = ProcessMarshaller.fromJson(processAsJson).valueOr(err => throw new IllegalArgumentException(err.msg))
        process.metaData.typeSpecificData match {
          case stream: StreamMetaData =>
            stream.parallelism
              .map(definedParallelism => OptionT.some[Future](definedParallelism))
              .getOrElse(OptionT(getJobManagerConfig.map { config =>
                val defaultParallelism = config.get(CoreOptions.DEFAULT_PARALLELISM)
                logger.debug(s"Not specified parallelism for process: ${process.metaData.id}, will be used default configured on jobmanager: $defaultParallelism")
                Option(defaultParallelism)
              }))
          case _ => OptionT.none
        }
      case CustomProcess(_) =>
        OptionT.none
    }
  }

  protected def getClusterOverview: Future[ClusterOverview]

  protected def getJobManagerConfig: Future[Configuration]

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

  override def invokeCustomAction(actionRequest: CustomActionRequest,
                                  processDeploymentData: ProcessDeploymentData): Future[Either[CustomActionError, CustomActionResult]] =
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

  private def checkIfJobIsCompatible(savepointPath: String, processDeploymentData: ProcessDeploymentData, processVersion: ProcessVersion): Future[Unit] =
    processDeploymentData match {
      case GraphProcess(processAsJson) if shouldVerifyBeforeDeploy =>
        verification.verify(processVersion, processAsJson, savepointPath)
      case _ => Future.successful(())
    }

  private def stopSavingSavepoint(processVersion: ProcessVersion, deploymentId: ExternalDeploymentId, processDeploymentData: ProcessDeploymentData): Future[String] = {
    logger.debug(s"Making savepoint of  ${processVersion.processName}. Deployment: $deploymentId")
    for {
      savepointResult <- makeSavepoint(deploymentId, savepointDir = None)
      savepointPath = savepointResult.path
      _ <- checkIfJobIsCompatible(savepointPath, processDeploymentData, processVersion)
      _ <- cancel(deploymentId)
    } yield savepointPath
  }



  private def prepareProgramMainClass(processDeploymentData: ProcessDeploymentData) : String = {
    processDeploymentData match {
      case GraphProcess(_) => mainClassName
      case CustomProcess(mainClass) => mainClass
    }
  }

  protected def cancel(deploymentId: ExternalDeploymentId): Future[Unit]

  protected def makeSavepoint(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult]

  protected def stop(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult]

  protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Option[ExternalDeploymentId]]

  override def processStateDefinitionManager: ProcessStateDefinitionManager = FlinkProcessStateDefinitionManager
}

object FlinkDeploymentManager {

  def prepareProgramArgs(serializedConfig: String,
                         processVersion: ProcessVersion,
                         deploymentData: DeploymentData,
                         processDeploymentData: ProcessDeploymentData) : List[String] = {
    processDeploymentData match {
      case GraphProcess(processAsJson) =>
        List(processAsJson, processVersion.asJson.spaces2, deploymentData.asJson.spaces2, serializedConfig)
      case CustomProcess(_) =>
        List(processVersion.processName.value, serializedConfig)
    }
  }

  case class NotEnoughSlotsException(availableSlots: Int, totalSlots: Int, requestedSlots: Int)
    extends IllegalArgumentException(s"There is not enough free slots on Flink cluster. Available slots: $availableSlots, total: $totalSlots, requested: $requestedSlots")

  object NotEnoughSlotsException {
    def apply(clusterOverview: ClusterOverview, requestedSlots: Int): NotEnoughSlotsException =
      NotEnoughSlotsException(availableSlots = clusterOverview.`slots-available`, totalSlots = clusterOverview.`slots-total`, requestedSlots = requestedSlots)
  }

}
