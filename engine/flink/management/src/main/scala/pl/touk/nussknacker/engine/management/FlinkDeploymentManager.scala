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
import pl.touk.nussknacker.engine.management.FlinkDeploymentManager.{NotEnoughSlotsException, SlotsBalance, prepareProgramArgs}
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.{ClusterOverview, ExecutionConfig}
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
      _ <- OptionT(checkRequiredSlotsExceedAvailableSlots(processDeploymentData, Some(deploymentId)).map(Option(_)))
      maybeSavePoint <- OptionT.liftF(stopSavingSavepoint(processVersion, deploymentId, processDeploymentData))
    } yield {
      logger.info(s"Deploying $processName. Saving savepoint finished")
      maybeSavePoint
    }

    for {
      maybeSavepoint <- stoppingResult.value
      // In case of redeploy we double check required slots which is not bad because can be some run between jobs and it is better to check it again
      _ <- checkRequiredSlotsExceedAvailableSlots(processDeploymentData, None)
      runResult <- runProgram(processName,
        prepareProgramMainClass(processDeploymentData),
        prepareProgramArgs(modelData.inputConfigDuringExecution.serialized, processVersion, deploymentData, processDeploymentData),
        savepointPath.orElse(maybeSavepoint))
    } yield runResult
  }

  // public for tests purpose
  def checkRequiredSlotsExceedAvailableSlots(processDeploymentData: ProcessDeploymentData, currentlyDeployedJobId: Option[ExternalDeploymentId]): Future[Unit] = {
    val collectedSlotsCheckInputs = for {
      slotsBalance <- determineSlotsBalance(processDeploymentData, currentlyDeployedJobId)
      clusterOverview <- OptionT(getClusterOverview.map(Option(_)))
    } yield (slotsBalance, clusterOverview)

    val checkResult = for {
      collectedInputs <- OptionT(collectedSlotsCheckInputs.value.recover {
        case NonFatal(ex) =>
          logger.warn("Error during collecting inputs needed for available slots checking. Slots checking will be omitted", ex)
          None
      })
      (slotsBalance, clusterOverview) = collectedInputs
      _ <- OptionT(
        if (slotsBalance.value > clusterOverview.`slots-available`)
          Future.failed(NotEnoughSlotsException(clusterOverview, slotsBalance))
        else
          Future.successful(Option(())))
    } yield ()
    checkResult.value.map(_ => Unit)
  }

  private def determineSlotsBalance(processDeploymentData: ProcessDeploymentData, currentlyDeployedJobId: Option[ExternalDeploymentId]): OptionT[Future, SlotsBalance] = {
    processDeploymentData match {
      case GraphProcess(processAsJson) =>
        val process = ProcessMarshaller.fromJson(processAsJson).valueOr(err => throw new IllegalArgumentException(err.msg))
        process.metaData.typeSpecificData match {
          case stream: StreamMetaData =>
            val requiredSlotsFuture = for {
              releasedSlots <- slotsThatWillBeReleasedAfterJobCancel(currentlyDeployedJobId)
              allocatedSlots <- slotsAllocatedByProcessThatWilBeDeployed(stream, process.metaData.id)
            } yield Option(SlotsBalance(releasedSlots, allocatedSlots))
            OptionT(requiredSlotsFuture)
          case _ => OptionT.none
        }
      case CustomProcess(_) =>
        OptionT.none
    }
  }
  private def slotsThatWillBeReleasedAfterJobCancel(currentlyDeployedJobId: Option[ExternalDeploymentId]): Future[Int] = {
    currentlyDeployedJobId
      .map(deploymentId => getJobConfig(deploymentId.value).map(_.`job-parallelism`))
      .getOrElse(Future.successful(0))
  }

  private def slotsAllocatedByProcessThatWilBeDeployed(stream: StreamMetaData, processId: String): Future[Int] = {
    stream.parallelism
      .map(definedParallelism => Future.successful(definedParallelism))
      .getOrElse(getJobManagerConfig.map { config =>
        val defaultParallelism = config.get(CoreOptions.DEFAULT_PARALLELISM)
        logger.debug(s"Not specified parallelism for process: $processId, will be used default configured on jobmanager: $defaultParallelism")
        defaultParallelism
      })
  }

  protected def getClusterOverview: Future[ClusterOverview]

  protected def getJobManagerConfig: Future[Configuration]

  protected def getJobConfig(jobId: String): Future[ExecutionConfig]

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

  case class NotEnoughSlotsException(availableSlots: Int, totalSlots: Int, slotsBalance: SlotsBalance)
    extends IllegalArgumentException(s"Not enough free slots on Flink cluster. Available slots: $availableSlots, requested: ${Math.max(0, slotsBalance.value)}. ${
      if (slotsBalance.allocated > 1)
        "Decrease scenario's parallelism or extend Flink cluster resources"
      else
        "Extend resources of Flink cluster resources"
    }")

  object NotEnoughSlotsException {
    def apply(clusterOverview: ClusterOverview, slotsBalance: SlotsBalance): NotEnoughSlotsException =
      NotEnoughSlotsException(availableSlots = clusterOverview.`slots-available`, totalSlots = clusterOverview.`slots-total`, slotsBalance = slotsBalance)
  }

  case class SlotsBalance(released: Int, allocated: Int) {
    def value: Int = allocated - released
  }

}
