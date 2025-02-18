package pl.touk.nussknacker.engine.management

import cats.data.NonEmptyList
import cats.implicits._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobID, JobStatus}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.scheduler.services._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.{
  FlinkMiniClusterScenarioStateVerifier,
  FlinkMiniClusterScenarioTestRunner
}
import pl.touk.nussknacker.engine.flink.minicluster.util.DurationToRetryPolicyConverterOps.DurationOps
import pl.touk.nussknacker.engine.management.FlinkDeploymentManager.DeploymentIdOps
import pl.touk.nussknacker.engine.management.jobrunner.FlinkScenarioJobRunner
import pl.touk.nussknacker.engine.management.rest.FlinkClient
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.JobOverview
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.WithDataFreshnessStatusUtils.WithDataFreshnessStatusMapOps
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerDependencies, newdeployment}

import scala.concurrent.Future

class FlinkDeploymentManager(
    modelData: BaseModelData,
    dependencies: DeploymentManagerDependencies,
    flinkConfig: FlinkConfig,
    miniClusterWithServicesOpt: Option[FlinkMiniClusterWithServices],
    client: FlinkClient,
    jobRunner: FlinkScenarioJobRunner
) extends DeploymentManager
    with LazyLogging {

  import dependencies._

  private val slotsChecker = new FlinkSlotsChecker(client)

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

  // Flink has a retention for job overviews so we can't rely on this to distinguish between statuses:
  // - job is finished without troubles
  // - job has failed
  // So we synchronize the information that the job was finished by marking deployments actions as execution finished
  // and treat another case as ProblemStateStatus.shouldBeRunning (see InconsistentStateDetector)
  // TODO: We should synchronize the status of deployment more explicitly as we already do in periodic case
  //       See PeriodicProcessService.synchronizeDeploymentsStates and remove the InconsistentStateDetector
  // FIXME abr move to reconciliation
  private def postprocess(
      idWithName: ProcessIdWithName,
      statusDetailsList: List[DeploymentStatusDetails]
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
      _ <- checkRequiredSlotsExceedAvailableSlots(canonicalProcess, oldJobs.map(_.jid))
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
      _ = {
        logger.debug(s"Starting to deploy scenario: $processName with savepoint $savepointPath")
      }
      jobIdOpt <- jobRunner.runScenarioJob(
        command,
        savepointPath,
      )
      _ <- jobIdOpt.map(waitForDuringDeployFinished(processName, _)).getOrElse(Future.successful(()))
    } yield jobIdOpt.map(_.toHexString).map(ExternalDeploymentId(_))
  }

  private def stopOldJobsIfNeeded(command: DMRunDeploymentCommand) = {
    import command._

    command.updateStrategy match {
      case DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(_) =>
        for {
          oldJobs <- oldJobsToStop(processVersion)
          externalDeploymentIds = oldJobs
            .sortBy(_.`start-time`)(Ordering[Long].reverse)
            .map(_.jid)
          savepoints <- Future.sequence(
            externalDeploymentIds.map(stopSavingSavepoint(processVersion, _, canonicalProcess))
          )
        } yield savepoints
      case DeploymentUpdateStrategy.DontReplaceDeployment =>
        Future.successful(List.empty)
    }
  }

  private def oldJobsToStop(processVersion: ProcessVersion): Future[List[JobOverview]] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    getScenarioDeploymentsStatusesWithJobOverview(processVersion.processName)
      .map(_.value.collect {
        case (details, jobOverview) if SimpleStateStatus.DefaultFollowingDeployStatuses.contains(details.status) =>
          jobOverview
      })
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

  private def requireSingleRunningJob[T](
      processName: ProcessName,
      statusDetailsPredicate: DeploymentStatusDetails => Boolean
  )(
      action: JobID => Future[T]
  ): Future[T] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    getScenarioDeploymentsStatusesWithJobOverview(processName).flatMap { statusesWithJobOverviews =>
      val runningJobIds = statusesWithJobOverviews.value.collect {
        case (details @ DeploymentStatusDetails(SimpleStateStatus.Running, Some(_), _), jobOverview)
            if statusDetailsPredicate(details) =>
          jobOverview.jid
      }
      runningJobIds match {
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
      deploymentId: JobID,
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

  override def getScenarioDeploymentsStatuses(
      scenarioName: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[DeploymentStatusDetails]]] = {
    getScenarioDeploymentsStatusesWithJobOverview(scenarioName).map(_.map(_.map(_._1)))
  }

  protected def getScenarioDeploymentsStatusesWithJobOverview(scenarioName: ProcessName)(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[(DeploymentStatusDetails, JobOverview)]]] = {
    getAllJobsStatusesFromFlink().map(_.getOrElse(scenarioName, List.empty))
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
                  .getJobDetails(deploymentId.toJobID)
                  .map(_.map { jobDetails =>
                    deploymentId -> FlinkStatusDetailsDeterminer
                      .toDeploymentStatus(JobStatus.valueOf(jobDetails.state), jobDetails.`status-counts`)
                  })
              }
          )
          .map(_.flatten.toMap)
      }

    }

  override def deploymentsStatusesQueryForAllScenariosSupport: DeploymentsStatusesQueryForAllScenariosSupport =
    new DeploymentsStatusesQueryForAllScenariosSupported {

      override def getAllScenariosDeploymentsStatuses()(
          implicit freshnessPolicy: DataFreshnessPolicy
      ): Future[WithDataFreshnessStatus[Map[ProcessName, List[DeploymentStatusDetails]]]] =
        getAllJobsStatusesFromFlink().map(_.map(_.mapValuesNow(_.map(_._1))))

    }

  override def schedulingSupport: SchedulingSupport = new SchedulingSupported {

    override def createScheduledExecutionPerformer(
        rawSchedulingConfig: Config,
    ): ScheduledExecutionPerformer = FlinkScheduledExecutionPerformer.create(client, modelData, rawSchedulingConfig)

  }

  private def getAllJobsStatusesFromFlink()(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[Map[ProcessName, List[(DeploymentStatusDetails, JobOverview)]]]] = {
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
      jobId: JobID
  ): Future[Unit] = {
    flinkConfig.waitForDuringDeployFinish.toEnabledConfig
      .map { config =>
        retry
          .Pause(config.maxChecks, config.delay)
          .apply {
            implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
            getScenarioDeploymentsStatusesWithJobOverview(processName).map { statusesWithJobOverview =>
              statusesWithJobOverview.value
                .find { case (details, jobOverview) =>
                  jobOverview.jid == jobId && details.status == SimpleStateStatus.DuringDeploy
                }
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
    getScenarioDeploymentsStatusesWithJobOverview(scenarioName).map(_.value).flatMap { detailsWithJobOverview =>
      cancelEachMatchingJob(scenarioName, None, detailsWithJobOverview)
    }
  }

  private def cancelDeployment(command: DMCancelDeploymentCommand): Future[Unit] = {
    import command._
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    getScenarioDeploymentsStatusesWithJobOverview(scenarioName).map(_.value).flatMap { detailsWithJobOverview =>
      cancelEachMatchingJob(
        scenarioName,
        Some(deploymentId),
        detailsWithJobOverview.filter(_._1.deploymentId.contains(deploymentId))
      )
    }
  }

  private def cancelEachMatchingJob(
      processName: ProcessName,
      deploymentId: Option[DeploymentId],
      detailsWithJobOverview: List[(DeploymentStatusDetails, JobOverview)]
  ) = {
    detailsWithJobOverview.collect {
      case (details, jobOverview) if !SimpleStateStatus.isFinalOrTransitioningToFinalStatus(details.status) =>
        jobOverview.jid
    } match {
      case Nil =>
        logger.warn(
          s"Trying to cancel $processName${deploymentId.map(" with id: " + _).getOrElse("")} which is not active on Flink."
        )
        Future.successful(())
      case singleJobId :: Nil => client.cancel(singleJobId)
      case moreThanOneJobIds =>
        logger.warn(
          s"Found duplicate jobs of $processName${deploymentId.map(" with id: " + _).getOrElse("")}: $moreThanOneJobIds. Cancelling all in non terminal state."
        )
        Future.sequence(moreThanOneJobIds.map(client.cancel)).map(_ => ())
    }
  }

  private def checkRequiredSlotsExceedAvailableSlots(
      canonicalProcess: CanonicalProcess,
      currentlyDeployedJobsIds: List[JobID]
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

  implicit class DeploymentIdOps(did: newdeployment.DeploymentId) {
    def toJobID: JobID =
      new JobID(did.value.getLeastSignificantBits, did.value.getMostSignificantBits)
  }

}
