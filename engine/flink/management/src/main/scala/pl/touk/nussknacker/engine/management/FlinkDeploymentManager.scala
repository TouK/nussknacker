package pl.touk.nussknacker.engine.management

import cats.data.NonEmptyList
import cats.implicits._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax.EncoderOps
import org.apache.flink.api.common.{JobID, JobStatus}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.inconsistency.InconsistentStateDetector
import pl.touk.nussknacker.engine.api.deployment.scheduler.services._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.{
  FlinkMiniClusterScenarioStateVerifier,
  FlinkMiniClusterScenarioTestRunner
}
import pl.touk.nussknacker.engine.flink.minicluster.util.DurationToRetryPolicyConverterOps.DurationOps
import pl.touk.nussknacker.engine.management.FlinkDeploymentManager.{MainClassName, prepareProgramArgs}
import pl.touk.nussknacker.engine.management.rest.FlinkClient
import pl.touk.nussknacker.engine.util.WithDataFreshnessStatusUtils.WithDataFreshnessStatusMapOps
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerDependencies, newdeployment}

import scala.concurrent.Future

class FlinkDeploymentManager(
    modelData: BaseModelData,
    dependencies: DeploymentManagerDependencies,
    flinkConfig: FlinkConfig,
    client: FlinkClient,
) extends DeploymentManager
    with LazyLogging {

  import dependencies._

  private val modelJarProvider = new FlinkModelJarProvider(modelData.modelClassLoaderUrls)

  private val slotsChecker = new FlinkSlotsChecker(client)

  private val miniClusterWithServicesOpt = {
    FlinkMiniClusterFactory.createMiniClusterWithServicesIfConfigured(
      modelData.modelClassLoader,
      flinkConfig.miniCluster,
      flinkConfig.scenarioTesting,
      flinkConfig.scenarioStateVerification
    )
  }

  private val testRunner = new FlinkMiniClusterScenarioTestRunner(
    modelData,
    miniClusterWithServicesOpt
      .filter(_ => flinkConfig.scenarioTesting.reuseSharedMiniCluster),
    flinkConfig.scenarioTesting.parallelism,
    flinkConfig.scenarioTesting.timeout.toPausePolicy
  )

  private val verification = new FlinkMiniClusterScenarioStateVerifier(
    modelData,
    miniClusterWithServicesOpt
      .filter(_ => flinkConfig.scenarioStateVerification.reuseSharedMiniCluster),
    flinkConfig.scenarioStateVerification.timeout.toPausePolicy
  )

  private val statusDeterminer = new FlinkStatusDetailsDeterminer(modelData.namingStrategy, client.getJobConfig)

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
          client.stop(_, savepointDir)
        }
      case DMStopScenarioCommand(processName, savepointDir, _) =>
        requireSingleRunningJob(processName, _ => true) {
          client.stop(_, savepointDir)
        }
      case DMMakeScenarioSavepointCommand(processName, savepointDir) =>
        // TODO: savepoint for given deployment id
        requireSingleRunningJob(processName, _ => true) {
          client.makeSavepoint(_, savepointDir)
        }
      case DMTestScenarioCommand(_, canonicalProcess, scenarioTestData) =>
        testRunner.runTests(canonicalProcess, scenarioTestData)
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

    for {
      stoppedJobsSavepoints <- stopOldJobsIfNeeded(command)
      _ = {
        logger.info(s"Deploying $processName. ${NonEmptyList
            .fromList(stoppedJobsSavepoints)
            .map(_.toList.mkString("Saving savepoints finished: ", ", ", "."))
            .getOrElse("There was no job to stop.")}")
      }
      savepointPath = determineSavepointPath(command.updateStrategy, stoppedJobsSavepoints)
      // In case of redeploy we double check required slots which is not bad because can be some run between jobs and it is better to check it again
      _ <- checkRequiredSlotsExceedAvailableSlots(canonicalProcess, List.empty)
      runResult <- runProgram(
        processName,
        MainClassName,
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

  private def stopOldJobsIfNeeded(command: DMRunDeploymentCommand) = {
    import command._

    command.updateStrategy match {
      case DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(_) =>
        for {
          oldJobs <- oldJobsToStop(processVersion)
          externalDeploymentIds = oldJobs
            .sortBy(_.startTime)(Ordering[Option[Long]].reverse)
            .flatMap(_.externalDeploymentId)
          savepoints <- Future.sequence(
            externalDeploymentIds.map(stopSavingSavepoint(processVersion, _, canonicalProcess))
          )
        } yield savepoints
      case DeploymentUpdateStrategy.DontReplaceDeployment =>
        Future.successful(List.empty)
    }
  }

  private def oldJobsToStop(processVersion: ProcessVersion): Future[List[StatusDetails]] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    getProcessStates(processVersion.processName)
      .map(_.value.filter(details => SimpleStateStatus.DefaultFollowingDeployStatuses.contains(details.status)))
  }

  private def determineSavepointPath(updateStrategy: DeploymentUpdateStrategy, stoppedJobsSavepoints: List[String]) =
    updateStrategy match {
      case DeploymentUpdateStrategy.DontReplaceDeployment => None
      case DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
            StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
          ) =>
        // TODO: Better handle situation with more than one jobs stopped
        stoppedJobsSavepoints.headOption
      case DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
            StateRestoringStrategy.RestoreStateFromCustomSavepoint(savepointPath)
          ) =>
        Some(savepointPath)
    }

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

  private def stopSavingSavepoint(
      processVersion: ProcessVersion,
      deploymentId: ExternalDeploymentId,
      canonicalProcess: CanonicalProcess
  ): Future[String] = {
    logger.debug(s"Making savepoint of  ${processVersion.processName}. Deployment: $deploymentId")
    for {
      savepointResult <- client.makeSavepoint(deploymentId, savepointDir = None)
      savepointPath = savepointResult.path
      _ <- checkIfJobIsCompatible(savepointPath, canonicalProcess, processVersion)
      _ <- client.cancel(deploymentId)
    } yield savepointPath
  }

  private def checkIfJobIsCompatible(
      savepointPath: String,
      canonicalProcess: CanonicalProcess,
      processVersion: ProcessVersion
  ): Future[Unit] =
    if (flinkConfig.scenarioStateVerification.enabled)
      verification.verify(processVersion, canonicalProcess, savepointPath)
    else
      Future.successful(())

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    getAllProcessesStatesFromFlink().map(_.getOrElse(name, List.empty))
  }

  override val deploymentSynchronisationSupport: DeploymentSynchronisationSupport =
    new DeploymentSynchronisationSupported {

      override def getDeploymentStatusesToUpdate(
          deploymentIdsToCheck: Set[newdeployment.DeploymentId]
      ): Future[Map[newdeployment.DeploymentId, DeploymentStatus]] = {
        Future
          .sequence(
            deploymentIdsToCheck.toSeq
              .map { deploymentId =>
                client
                  .getJobDetails(toJobId(deploymentId))
                  .map(_.map { jobDetails =>
                    deploymentId -> FlinkStatusDetailsDeterminer
                      .toDeploymentStatus(JobStatus.valueOf(jobDetails.state), jobDetails.`status-counts`)
                  })
              }
          )
          .map(_.flatten.toMap)
      }

    }

  override def stateQueryForAllScenariosSupport: StateQueryForAllScenariosSupport =
    new StateQueryForAllScenariosSupported {

      override def getAllProcessesStates()(
          implicit freshnessPolicy: DataFreshnessPolicy
      ): Future[WithDataFreshnessStatus[Map[ProcessName, List[StatusDetails]]]] = getAllProcessesStatesFromFlink()

    }

  override def schedulingSupport: SchedulingSupport = new SchedulingSupported {

    override def createScheduledExecutionPerformer(
        rawSchedulingConfig: Config,
    ): ScheduledExecutionPerformer = FlinkScheduledExecutionPerformer.create(client, modelData, rawSchedulingConfig)

  }

  private def getAllProcessesStatesFromFlink()(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[Map[ProcessName, List[StatusDetails]]]] = {
    client
      .getJobsOverviews()
      .flatMap { result =>
        statusDeterminer
          .statusDetailsFromJobOverviews(result.value)
          .map(
            WithDataFreshnessStatus(_, cached = result.cached)
          ) // TODO: How to do it nicer?
      }
  }

  private def waitForDuringDeployFinished(
      processName: ProcessName,
      deploymentId: ExternalDeploymentId
  ): Future[Unit] = {
    flinkConfig.waitForDuringDeployFinish.toEnabledConfig
      .map { config =>
        retry
          .Pause(config.maxChecks, config.delay)
          .apply {
            implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
            getProcessStates(processName).map { statuses =>
              statuses.value
                .find(details =>
                  details.externalDeploymentId
                    .contains(deploymentId) && details.status == SimpleStateStatus.DuringDeploy
                )
                .map(Left(_))
                .getOrElse(Right(()))
            }
          }
          .map(
            _.getOrElse(
              throw new IllegalStateException(
                "Deploy execution finished, but job is still in during deploy state on Flink"
              )
            )
          )
      }
      .getOrElse(Future.successful(()))
  }

  protected def cancelScenario(command: DMCancelScenarioCommand): Future[Unit] = {
    import command._
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    getProcessStates(scenarioName).map(_.value).flatMap { statuses =>
      cancelEachMatchingJob(scenarioName, None, statuses)
    }
  }

  private def cancelDeployment(command: DMCancelDeploymentCommand): Future[Unit] = {
    import command._
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    getProcessStates(scenarioName).map(_.value).flatMap { statuses =>
      cancelEachMatchingJob(scenarioName, Some(deploymentId), statuses.filter(_.deploymentId.contains(deploymentId)))
    }
  }

  private def cancelEachMatchingJob(
      processName: ProcessName,
      deploymentId: Option[DeploymentId],
      statuses: List[StatusDetails]
  ) = {
    statuses.filterNot(details => SimpleStateStatus.isFinalOrTransitioningToFinalStatus(details.status)) match {
      case Nil =>
        logger.warn(
          s"Trying to cancel $processName${deploymentId.map(" with id: " + _).getOrElse("")} which is not active on Flink."
        )
        Future.successful(())
      case single :: Nil => client.cancel(single.externalDeploymentIdUnsafe)
      case moreThanOne @ (_ :: _ :: _) =>
        logger.warn(
          s"Found duplicate jobs of $processName${deploymentId.map(" with id: " + _).getOrElse("")}: $moreThanOne. Cancelling all in non terminal state."
        )
        Future.sequence(moreThanOne.map(_.externalDeploymentIdUnsafe).map(client.cancel)).map(_ => ())
    }
  }

  private def runProgram(
      processName: ProcessName,
      mainClass: String,
      args: List[String],
      savepointPath: Option[String],
      deploymentId: Option[newdeployment.DeploymentId]
  ): Future[Option[ExternalDeploymentId]] = {
    logger.debug(s"Starting to deploy scenario: $processName with savepoint $savepointPath")
    client.runProgram(
      modelJarProvider.getJobJar(),
      mainClass,
      args,
      savepointPath,
      deploymentId.map(toJobId)
    )
  }

  private def toJobId(did: newdeployment.DeploymentId) = {
    new JobID(did.value.getLeastSignificantBits, did.value.getMostSignificantBits).toHexString
  }

  private def checkRequiredSlotsExceedAvailableSlots(
      canonicalProcess: CanonicalProcess,
      currentlyDeployedJobsIds: List[ExternalDeploymentId]
  ): Future[Unit] = {
    if (flinkConfig.shouldCheckAvailableSlots) {
      slotsChecker.checkRequiredSlotsExceedAvailableSlots(canonicalProcess, currentlyDeployedJobsIds)
    } else
      Future.successful(())
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager = FlinkProcessStateDefinitionManager

  override def close(): Unit = {
    logger.info("Closing Flink Deployment Manager")
    miniClusterWithServicesOpt.foreach(_.close())
  }

}

object FlinkDeploymentManager {

  private[management] val MainClassName = "pl.touk.nussknacker.engine.process.runner.FlinkStreamingProcessMain"

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
