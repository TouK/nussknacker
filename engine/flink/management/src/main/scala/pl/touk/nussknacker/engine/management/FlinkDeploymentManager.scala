package pl.touk.nussknacker.engine.management

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.ModelData._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.inconsistency.InconsistentStateDetector
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.FlinkDeploymentManager.prepareProgramArgs
import pl.touk.nussknacker.engine.management.scenariotesting.{
  FlinkProcessTestRunner,
  FlinkProcessVerifier,
  ScenarioTestingMiniClusterWrapperFactory
}
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerDependencies, newdeployment}

import scala.concurrent.Future

abstract class FlinkDeploymentManager(
    modelData: BaseModelData,
    dependencies: DeploymentManagerDependencies,
    shouldVerifyBeforeDeploy: Boolean,
    mainClassName: String,
    scenarioTestingConfig: ScenarioTestingConfig
) extends DeploymentManager
    with LazyLogging {

  import dependencies._

  private lazy val scenarioTestingMiniClusterWrapperOpt =
    ScenarioTestingMiniClusterWrapperFactory.createIfConfigured(
      modelData.asInvokableModelData.modelClassLoader,
      scenarioTestingConfig
    )

  private lazy val testRunner = new FlinkProcessTestRunner(
    modelData.asInvokableModelData,
    scenarioTestingMiniClusterWrapperOpt.filter(_ => scenarioTestingConfig.reuseMiniClusterForScenarioTesting)
  )

  private lazy val verification = new FlinkProcessVerifier(
    modelData.asInvokableModelData,
    scenarioTestingMiniClusterWrapperOpt.filter(_ => scenarioTestingConfig.reuseMiniClusterForScenarioStateVerification)
  )

  /**
    * Gets status from engine, handles finished state, resolves possible inconsistency with lastAction and formats status using `ProcessStateDefinitionManager`
    */
  override def resolve(
      idWithName: ProcessIdWithName,
      statusDetails: List[StatusDetails],
      lastStateAction: Option[ProcessAction],
      latestVersionId: VersionId,
      deployedVersionId: Option[VersionId],
      currentlyPresentedVersionId: Option[VersionId],
  ): Future[ProcessState] = {
    for {
      actionAfterPostprocessOpt <- postprocess(idWithName, statusDetails)
      engineStateResolvedWithLastAction = InconsistentStateDetector.resolve(
        statusDetails,
        actionAfterPostprocessOpt.orElse(lastStateAction)
      )
    } yield processStateDefinitionManager.processState(
      engineStateResolvedWithLastAction,
      latestVersionId,
      deployedVersionId,
      currentlyPresentedVersionId,
    )
  }

  // Flink has a retention for job overviews so we can't rely on this to distinguish between statuses:
  // - job is finished without troubles
  // - job has failed
  // So we synchronize the information that the job was finished by marking deployments actions as execution finished
  // and treat another case as ProblemStateStatus.shouldBeRunning (see InconsistentStateDetector)
  // TODO: We should synchronize the status of deployment more explicitly as we already do in periodic case
  //       See PeriodicProcessService.synchronizeDeploymentsStates and remove the InconsistentStateDetector
  private def postprocess(
      idWithName: ProcessIdWithName,
      statusDetailsList: List[StatusDetails]
  ): Future[Option[ProcessAction]] = {
    val allDeploymentIdsAsCorrectActionIds =
      statusDetailsList.flatMap(details =>
        details.deploymentId.flatMap(_.toActionIdOpt).map(id => (id, details.status))
      )
    markEachFinishedDeploymentAsExecutionFinishedAndReturnLastStateAction(
      idWithName,
      allDeploymentIdsAsCorrectActionIds
    )
  }

  private def markEachFinishedDeploymentAsExecutionFinishedAndReturnLastStateAction(
      idWithName: ProcessIdWithName,
      deploymentActionStatuses: List[(ProcessActionId, StateStatus)]
  ): Future[Option[ProcessAction]] = {
    val finishedDeploymentActionsIds = deploymentActionStatuses.collect { case (id, SimpleStateStatus.Finished) =>
      id
    }
    Future.sequence(finishedDeploymentActionsIds.map(actionService.markActionExecutionFinished)).flatMap {
      markingResult =>
        Option(markingResult)
          .filter(_.contains(true))
          .map { _ =>
            actionService.getLastStateAction(idWithName.id)
          }
          .getOrElse(Future.successful(None))
    }
  }

  override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] =
    command match {
      case command: DMValidateScenarioCommand => validate(command)
      case command: DMRunDeploymentCommand    => runDeployment(command)
      case command: DMCancelDeploymentCommand => cancelDeployment(command)
      case command: DMCancelScenarioCommand   => cancelScenario(command)
      case DMStopDeploymentCommand(processName, deploymentId, savepointDir, _) =>
        requireSingleRunningJob(processName, _.deploymentId.contains(deploymentId)) {
          stop(_, savepointDir)
        }
      case DMStopScenarioCommand(processName, savepointDir, _) =>
        requireSingleRunningJob(processName, _ => true) {
          stop(_, savepointDir)
        }
      case DMMakeScenarioSavepointCommand(processName, savepointDir) =>
        // TODO: savepoint for given deployment id
        requireSingleRunningJob(processName, _ => true) {
          makeSavepoint(_, savepointDir)
        }
      case DMTestScenarioCommand(_, canonicalProcess, scenarioTestData) =>
        testRunner.runTestsAsync(canonicalProcess, scenarioTestData)
      case _: DMRunOffScheduleCommand => notImplemented
    }

  private def validate(command: DMValidateScenarioCommand): Future[Unit] = {
    import command._
    for {
      oldJobs <- command.updateStrategy match {
        case DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(_) => oldJobsToStop(processVersion)
        case DeploymentUpdateStrategy.DontReplaceDeployment                    => Future.successful(List.empty)
      }
      _ <- checkRequiredSlotsExceedAvailableSlots(canonicalProcess, oldJobs.flatMap(_.externalDeploymentId))
    } yield ()
  }

  protected def runDeployment(command: DMRunDeploymentCommand): Future[Option[ExternalDeploymentId]] = {
    import command._
    val processName = processVersion.processName

    val stoppingResult = command.updateStrategy match {
      case DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(_) =>
        for {
          oldJobs <- oldJobsToStop(processVersion)
          externalDeploymentIds = oldJobs
            .sortBy(_.startTime)(Ordering[Option[Long]].reverse)
            .flatMap(_.externalDeploymentId)
          savepoints <- Future.sequence(
            externalDeploymentIds.map(stopSavingSavepoint(processVersion, _, canonicalProcess))
          )
        } yield {
          logger.info(s"Deploying $processName. ${Option(savepoints)
              .filter(_.nonEmpty)
              .map(_.mkString("Saving savepoints finished: ", ", ", "."))
              .getOrElse("There was no job to stop.")}")
          savepoints
        }
      case DeploymentUpdateStrategy.DontReplaceDeployment =>
        Future.successful(List.empty)
    }
    for {
      savepointList <- stoppingResult
      // In case of redeploy we double check required slots which is not bad because can be some run between jobs and it is better to check it again
      _ <- checkRequiredSlotsExceedAvailableSlots(canonicalProcess, List.empty)
      savepointPath = command.updateStrategy match {
        case DeploymentUpdateStrategy.DontReplaceDeployment => None
        case DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
            ) =>
          // TODO: Better handle situation with more than one jobs stopped
          savepointList.headOption
        case DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
              StateRestoringStrategy.RestoreStateFromCustomSavepoint(savepointPath)
            ) =>
          Some(savepointPath)
      }
      runResult <- runProgram(
        processName,
        mainClassName,
        prepareProgramArgs(
          modelData.inputConfigDuringExecution.serialized,
          processVersion,
          deploymentData,
          canonicalProcess
        ),
        savepointPath,
        command.deploymentData.deploymentId.toNewDeploymentIdOpt
      )
      _ <- runResult.map(waitForDuringDeployFinished(processName, _)).getOrElse(Future.successful(()))
    } yield runResult
  }

  protected def waitForDuringDeployFinished(processName: ProcessName, deploymentId: ExternalDeploymentId): Future[Unit]

  private def oldJobsToStop(processVersion: ProcessVersion): Future[List[StatusDetails]] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    getProcessStates(processVersion.processName)
      .map(_.value.filter(details => SimpleStateStatus.DefaultFollowingDeployStatuses.contains(details.status)))
  }

  protected def checkRequiredSlotsExceedAvailableSlots(
      canonicalProcess: CanonicalProcess,
      currentlyDeployedJobsIds: List[ExternalDeploymentId]
  ): Future[Unit]

  private def requireSingleRunningJob[T](processName: ProcessName, statusDetailsPredicate: StatusDetails => Boolean)(
      action: ExternalDeploymentId => Future[T]
  ): Future[T] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    getProcessStates(processName).flatMap { statuses =>
      val runningDeploymentIds = statuses.value.filter(statusDetailsPredicate).collect {
        case StatusDetails(SimpleStateStatus.Running, _, Some(deploymentId), _, _, _, _) => deploymentId
      }
      runningDeploymentIds match {
        case Nil =>
          Future.failed(new IllegalStateException(s"Job $processName not found"))
        case single :: Nil =>
          action(single)
        case moreThanOne =>
          Future.failed(new IllegalStateException(s"Multiple running jobs: ${moreThanOne.mkString(", ")}"))
      }
    }
  }

  private def checkIfJobIsCompatible(
      savepointPath: String,
      canonicalProcess: CanonicalProcess,
      processVersion: ProcessVersion
  ): Future[Unit] =
    if (shouldVerifyBeforeDeploy)
      verification.verify(processVersion, canonicalProcess, savepointPath)
    else Future.successful(())

  private def stopSavingSavepoint(
      processVersion: ProcessVersion,
      deploymentId: ExternalDeploymentId,
      canonicalProcess: CanonicalProcess
  ): Future[String] = {
    logger.debug(s"Making savepoint of  ${processVersion.processName}. Deployment: $deploymentId")
    for {
      savepointResult <- makeSavepoint(deploymentId, savepointDir = None)
      savepointPath = savepointResult.path
      _ <- checkIfJobIsCompatible(savepointPath, canonicalProcess, processVersion)
      _ <- cancelFlinkJob(deploymentId)
    } yield savepointPath
  }

  protected def cancelScenario(command: DMCancelScenarioCommand): Future[Unit]

  protected def cancelDeployment(command: DMCancelDeploymentCommand): Future[Unit]

  protected def cancelFlinkJob(deploymentId: ExternalDeploymentId): Future[Unit]

  protected def makeSavepoint(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult]

  protected def stop(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult]

  protected def runProgram(
      processName: ProcessName,
      mainClass: String,
      args: List[String],
      savepointPath: Option[String],
      // TODO: make it mandatory - see TODO in newdeployment.DeploymentService
      deploymentId: Option[newdeployment.DeploymentId]
  ): Future[Option[ExternalDeploymentId]]

  override def processStateDefinitionManager: ProcessStateDefinitionManager = FlinkProcessStateDefinitionManager

  override def close(): Unit = {
    logger.info("Closing Flink Deployment Manager")
    scenarioTestingMiniClusterWrapperOpt.foreach(_.close())
  }

}

object FlinkDeploymentManager {

  def prepareProgramArgs(
      serializedConfig: String,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess
  ): List[String] =
    List(
      canonicalProcess.asJson.spaces2,
      processVersion.asJson.spaces2,
      deploymentData.asJson.spaces2,
      serializedConfig
    )

}
