package pl.touk.nussknacker.engine.management

import com.github.benmanes.caffeine.cache.{AsyncCache, Caffeine}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobStatus
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.namespaces.{FlinkUsageKey, NamingContext}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.management.FlinkRestManager.JobDetails
import pl.touk.nussknacker.engine.management.rest.{HttpFlinkClient, flinkRestModel}
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.JobOverview
import scala.compat.java8.FutureConverters._
import sttp.client3._

import scala.concurrent.{ExecutionContext, Future}

class FlinkRestManager(config: FlinkConfig, modelData: BaseModelData, mainClassName: String)(
    implicit ec: ExecutionContext,
    backend: SttpBackend[Future, Any],
    deploymentService: ProcessingTypeDeploymentService
) extends FlinkDeploymentManager(modelData, config.shouldVerifyBeforeDeploy, mainClassName)
    with LazyLogging {

  private val modelJarProvider = new FlinkModelJarProvider(modelData.modelClassLoaderUrls)

  private val client = new HttpFlinkClient(config)

  private val slotsChecker = new FlinkSlotsChecker(client)

  private val jobDetailsCache: AsyncCache[String, Option[JobDetails]] =
    Caffeine
      .newBuilder()
      .maximumSize(config.jobConfigsCacheSize)
      .buildAsync[String, Option[JobDetails]]()

  override def getFreshProcessStates(name: ProcessName): Future[List[StatusDetails]] = {
    val preparedName =
      modelData.objectNaming.prepareName(name.value, modelData.modelConfig, new NamingContext(FlinkUsageKey))
    client
      .findJobsByName(preparedName)
      .flatMap(jobs =>
        Future.sequence(
          jobs
            .map(job =>
              withJobDetails(job.jid, name).map { jobDetails =>
                // TODO: return error when there's no correct version in process
                // currently we're rather lax on this, so that this change is backward-compatible
                // we log debug here for now, since it's invoked v. often
                if (jobDetails.isEmpty) {
                  logger.debug(s"No correct job details in deployed scenario: ${job.name}")
                }
                StatusDetails(
                  mapJobStatus(job),
                  jobDetails.flatMap(_.deploymentId),
                  Some(ExternalDeploymentId(job.jid)),
                  version = jobDetails.map(_.version),
                  startTime = Some(job.`start-time`),
                  attributes = Option.empty,
                  errors = List.empty
                )
              }
            )
        )
      )
  }

  private def toJobStatus(overview: JobOverview): JobStatus = {
    import org.apache.flink.api.common.JobStatus
    JobStatus.valueOf(overview.state)
  }

  // NOTE: Flink <1.10 compatibility - protected to make it easier to work with Flink 1.9, JobStatus changed package, so we use String in case class
  protected def mapJobStatus(overview: JobOverview): StateStatus = {
    toJobStatus(overview) match {
      case JobStatus.RUNNING if ensureTasksRunning(overview) => SimpleStateStatus.Running
      case s if checkDuringDeployForNotRunningJob(s)         => SimpleStateStatus.DuringDeploy
      case JobStatus.FINISHED                                => SimpleStateStatus.Finished
      case JobStatus.RESTARTING                              => SimpleStateStatus.Restarting
      case JobStatus.CANCELED                                => SimpleStateStatus.Canceled
      case JobStatus.CANCELLING                              => SimpleStateStatus.DuringCancel
      // The job is not technically running, but should be in a moment
      case JobStatus.RECONCILING | JobStatus.CREATED | JobStatus.SUSPENDED => SimpleStateStatus.Running
      case JobStatus.FAILING => ProblemStateStatus.Failed // redeploy allowed, handle with restartStrategy
      case JobStatus.FAILED  => ProblemStateStatus.Failed // redeploy allowed, handle with restartStrategy
      case _ =>
        throw new IllegalStateException() // TODO: drop support for Flink 1.11 & inline `checkDuringDeployForNotRunningJob` so we could benefit from pattern matching exhaustive check
    }

  }

  protected def ensureTasksRunning(overview: JobOverview): Boolean = {
    // We sum running and finished tasks because for batch jobs some tasks can be already finished but the others are still running.
    // We don't handle correctly case when job creates some tasks lazily e.g. in batch case. Without knowledge about what
    // kind of job is deployed, we don't know if it is such case or it is just a streaming job which is not fully running yet
    overview.tasks.running + overview.tasks.finished == overview.tasks.total
  }

  // TODO: drop support for Flink 1.11 & inline `checkDuringDeployForNotRunningJob` so we could benefit from pattern matching exhaustive check
  protected def checkDuringDeployForNotRunningJob(s: JobStatus): Boolean = {
    // Flink return running status even if some tasks are scheduled or initializing
    s == JobStatus.RUNNING || s == JobStatus.INITIALIZING
  }

  override protected def waitForDuringDeployFinished(
      processName: ProcessName,
      deploymentId: ExternalDeploymentId
  ): Future[Unit] = {
    config.waitForDuringDeployFinish.toEnabledConfig
      .map { config =>
        retry
          .Pause(config.maxChecks, config.delay)
          .apply {
            getFreshProcessStates(processName).map { statuses =>
              statuses
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

  private def withJobDetails(jobId: String, name: ProcessName): Future[Option[JobDetails]] = {
    def toJobDetails(executionConfig: flinkRestModel.ExecutionConfig): Option[JobDetails] = {
      val userConfig = executionConfig.`user-config`
      for {
        version <- userConfig.get("versionId").flatMap(_.asString).map(_.toLong).map(VersionId(_))
        user    <- userConfig.get("user").map(_.asString.getOrElse(""))
        modelVersion = userConfig.get("modelVersion").flatMap(_.asString).map(_.toInt)
        processId    = ProcessId(userConfig.get("processId").flatMap(_.asString).map(_.toLong).getOrElse(-1L))
        deploymentId = userConfig.get("deploymentId").flatMap(_.asString).map(DeploymentId(_))
      } yield {
        val versionDetails = ProcessVersion(version, name, processId, user, modelVersion)
        JobDetails(versionDetails, deploymentId)
      }
    }

    jobDetailsCache
      .get(jobId, (key, _) => client.getJobConfig(key).map(toJobDetails).toJava.toCompletableFuture)
      .toScala
  }

  override def cancel(processName: ProcessName, user: User): Future[Unit] = {
    getFreshProcessStates(processName).flatMap { statuses =>
      cancelEachMatchingJob(processName, None, statuses)
    }
  }

  override def cancel(processName: ProcessName, deploymentId: DeploymentId, user: User): Future[Unit] = {
    getFreshProcessStates(processName).flatMap { statuses =>
      cancelEachMatchingJob(processName, Some(deploymentId), statuses.filter(_.deploymentId.contains(deploymentId)))
    }
  }

  private def cancelEachMatchingJob(
      processName: ProcessName,
      deploymentId: Option[DeploymentId],
      statuses: List[StatusDetails]
  ) = {
    statuses.filterNot(details => SimpleStateStatus.isFinalStatus(details.status)) match {
      case Nil =>
        logger.warn(
          s"Trying to cancel $processName${deploymentId.map(" with id: " + _).getOrElse("")} which is not present or finished on Flink."
        )
        Future.successful(())
      case single :: Nil => cancelJob(single)
      case moreThanOne @ (_ :: _ :: _) =>
        logger.warn(
          s"Found duplicate jobs of $processName${deploymentId.map(" with id: " + _).getOrElse("")}: $moreThanOne. Cancelling all in non terminal state."
        )
        Future.sequence(moreThanOne.map(cancelJob)).map(_ => ())
    }
  }

  private def cancelJob(details: StatusDetails) = {
    cancel(
      details.externalDeploymentId.getOrElse(
        throw new IllegalStateException(
          "Error during cancelling scenario: returned status details has no external deployment id"
        )
      )
    )
  }

  override protected def cancel(deploymentId: ExternalDeploymentId): Future[Unit] = {
    client.cancel(deploymentId)
  }

  override protected def makeSavepoint(
      deploymentId: ExternalDeploymentId,
      savepointDir: Option[String]
  ): Future[SavepointResult] = {
    client.makeSavepoint(deploymentId, savepointDir)
  }

  override protected def stop(
      deploymentId: ExternalDeploymentId,
      savepointDir: Option[String]
  ): Future[SavepointResult] = {
    client.stop(deploymentId, savepointDir)
  }

  override protected def runProgram(
      processName: ProcessName,
      mainClass: String,
      args: List[String],
      savepointPath: Option[String]
  ): Future[Option[ExternalDeploymentId]] = {
    logger.debug(s"Starting to deploy scenario: $processName with savepoint $savepointPath")
    client.runProgram(modelJarProvider.getJobJar(), mainClass, args, savepointPath)
  }

  override protected def checkRequiredSlotsExceedAvailableSlots(
      canonicalProcess: CanonicalProcess,
      currentlyDeployedJobsIds: List[ExternalDeploymentId]
  ): Future[Unit] = {
    if (config.shouldCheckAvailableSlots) {
      slotsChecker.checkRequiredSlotsExceedAvailableSlots(canonicalProcess, currentlyDeployedJobsIds)
    } else
      Future.successful(())
  }

  override def close(): Unit = {
    logger.info("Closing Flink REST manager")
  }

}

object FlinkRestManager {

  // TODO: deploymentId is optional to handle situation when on Flink there is old version of runtime and in designer is the new one.
  //       After fully deploy of new version it should be mandatory
  private case class JobDetails(version: ProcessVersion, deploymentId: Option[DeploymentId])

}
