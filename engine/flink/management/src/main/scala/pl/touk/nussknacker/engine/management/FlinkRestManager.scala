package pl.touk.nussknacker.engine.management

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobStatus
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.FlinkRestManager.JobDetails
import pl.touk.nussknacker.engine.management.rest.FlinkClient
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.JobOverview
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerDependencies, newdeployment}

import scala.concurrent.Future

class FlinkRestManager(
    client: FlinkClient,
    config: FlinkConfig,
    modelData: BaseModelData,
    dependencies: DeploymentManagerDependencies,
    mainClassName: String
) extends FlinkDeploymentManager(modelData, dependencies, config.shouldVerifyBeforeDeploy, mainClassName)
    with LazyLogging {

  import dependencies._

  private val modelJarProvider = new FlinkModelJarProvider(modelData.modelClassLoaderUrls)

  private val slotsChecker = new FlinkSlotsChecker(client)

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    val preparedName = modelData.namingStrategy.prepareName(name.value)

    client
      .getJobsOverviews()
      .flatMap(result =>
        Future
          .sequence(
            result.value
              .filter(_.name == preparedName)
              .map(job =>
                withJobDetails(job.jid, name).map { jobDetails =>
                  // TODO: return error when there's no correct version in process
                  // currently we're rather lax on this, so that this change is backward-compatible
                  // we log debug here for now, since it's invoked v. often
                  if (jobDetails.isEmpty) {
                    logger.debug(s"No correct job details in deployed scenario: ${job.name}")
                  }
                  StatusDetails(
                    SimpleStateStatus.fromDeploymentStatus(mapJobStatus(job)),
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
          .map(WithDataFreshnessStatus(_, cached = result.cached)) // TODO: How to do it nicer?
      )
  }

  override val deploymentSynchronisationSupport: DeploymentSynchronisationSupport =
    new DeploymentSynchronisationSupported {

      override def getDeploymentStatusesToUpdate: Future[Map[newdeployment.DeploymentId, DeploymentStatus]] = {
        client.getJobsOverviews()(DataFreshnessPolicy.Fresh).map(_.value).flatMap { jobsOverviews =>
          jobsOverviews
            .map { jobOverview =>
              val status = mapJobStatus(jobOverview)
              client.getJobConfig(jobOverview.jid).map { jobConfig =>
                val deploymentIdOpt = jobConfig.`user-config`.get("deploymentId")
                if (deploymentIdOpt.isEmpty) {
                  logger.warn(
                    s"Job [id=${jobOverview.jid}, name=${jobOverview.name}] has no deploymentId. " +
                      s"It will be ignored during deployment status synchronization"
                  )
                }
                deploymentIdOpt
                  .flatMap(_.asString)
                  .flatMap(newdeployment.DeploymentId.fromString)
                  .map(_ -> status)
              }
            }
            .sequence
            .map(_.flatten.toMap)
        }
      }

    }

  // NOTE: Flink <1.10 compatibility - protected to make it easier to work with Flink 1.9, JobStatus changed package, so we use String in case class
  protected def mapJobStatus(overview: JobOverview): DeploymentStatus = {
    toJobStatus(overview) match {
      case JobStatus.RUNNING if ensureTasksRunning(overview) => DeploymentStatus.Running
      case s if checkDuringDeployForNotRunningJob(s)         => DeploymentStatus.DuringDeploy
      case JobStatus.FINISHED                                => DeploymentStatus.Finished
      case JobStatus.RESTARTING                              => DeploymentStatus.Restarting
      case JobStatus.CANCELED                                => DeploymentStatus.Canceled
      case JobStatus.CANCELLING                              => DeploymentStatus.DuringCancel
      // The job is not technically running, but should be in a moment
      case JobStatus.RECONCILING | JobStatus.CREATED | JobStatus.SUSPENDED => DeploymentStatus.Running
      case JobStatus.FAILING | JobStatus.FAILED =>
        DeploymentStatus.Problem.Failed // redeploy allowed, handle with restartStrategy
      case _ =>
        throw new IllegalStateException() // TODO: drop support for Flink 1.11 & inline `checkDuringDeployForNotRunningJob` so we could benefit from pattern matching exhaustive check
    }
  }

  private def toJobStatus(overview: JobOverview): JobStatus = {
    import org.apache.flink.api.common.JobStatus
    JobStatus.valueOf(overview.state)
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

  private def withJobDetails(jobId: String, name: ProcessName): Future[Option[JobDetails]] = {
    client.getJobConfig(jobId).map { executionConfig =>
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
  }

  override protected def cancelScenario(command: DMCancelScenarioCommand): Future[Unit] = {
    import command._
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    getProcessStates(scenarioName).map(_.value).flatMap { statuses =>
      cancelEachMatchingJob(scenarioName, None, statuses)
    }
  }

  override protected def cancelDeployment(command: DMCancelDeploymentCommand): Future[Unit] = {
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
    statuses.filterNot(details => SimpleStateStatus.isFinalStatus(details.status)) match {
      case Nil =>
        logger.warn(
          s"Trying to cancel $processName${deploymentId.map(" with id: " + _).getOrElse("")} which is not present or finished on Flink."
        )
        Future.successful(())
      case single :: Nil => cancelFlinkJob(single)
      case moreThanOne @ (_ :: _ :: _) =>
        logger.warn(
          s"Found duplicate jobs of $processName${deploymentId.map(" with id: " + _).getOrElse("")}: $moreThanOne. Cancelling all in non terminal state."
        )
        Future.sequence(moreThanOne.map(cancelFlinkJob)).map(_ => ())
    }
  }

  private def cancelFlinkJob(details: StatusDetails): Future[Unit] = {
    cancelFlinkJob(
      details.externalDeploymentId.getOrElse(
        throw new IllegalStateException(
          "Error during cancelling scenario: returned status details has no external deployment id"
        )
      )
    )
  }

  override protected def cancelFlinkJob(deploymentId: ExternalDeploymentId): Future[Unit] = {
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
