package pl.touk.nussknacker.engine.management

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobStatus
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.namespaces.{FlinkUsageKey, NamingContext}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.management.rest.HttpFlinkClient
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.JobOverview
import sttp.client._

import java.io.File
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}


class FlinkRestManager(config: FlinkConfig, modelData: ModelData, mainClassName: String)
                      (implicit backend: SttpBackend[Future, Nothing, NothingT])
    extends FlinkDeploymentManager(modelData, config.shouldVerifyBeforeDeploy, mainClassName) with LazyLogging {

  protected lazy val jarFile: File = new FlinkModelJar().buildJobJar(modelData)

  private val client = new HttpFlinkClient(config)

  private val slotsChecker = new FlinkSlotsChecker(client)

  /*
  It's ok to have many jobs with same name, however:
  - there MUST be at most 1 job in *non-terminal* state with given name
  - deployment is possible IFF there is NO job in *non-terminal* state with given name
 */
  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = withJobOverview(name)(
    whenNone = Future.successful(None),
    whenDuplicates = duplicates => Future.successful(Some(ProcessState(
      Some(ExternalDeploymentId(duplicates.head.jid)),
      //we cannot have e.g. Failed here as we don't want to allow more jobs
      FlinkStateStatus.MultipleJobsRunning,
      definitionManager = processStateDefinitionManager,
      version = Option.empty,
      attributes = Option.empty,
      startTime = Some(duplicates.head.`start-time`),
      errors = List(s"Expected one job, instead: ${duplicates.map(job => s"${job.jid} - ${job.state}").mkString(", ")}"))
    )),
    whenSingle = job => withVersion(job.jid, name).map { version =>
      //TODO: return error when there's no correct version in process
      //currently we're rather lax on this, so that this change is backward-compatible
      //we log debug here for now, since it's invoked v. often
      if (version.isEmpty) {
        logger.debug(s"No correct version in deployed scenario: ${job.name}")
      }
      Some(ProcessState(
        Some(ExternalDeploymentId(job.jid)),
        mapJobStatus(job),
        version = version,
        definitionManager = processStateDefinitionManager,
        startTime = Some(job.`start-time`),
        attributes = Option.empty,
        errors = List.empty
      ))
    }
  )

  private def withJobOverview[T](name: ProcessName)(whenNone: => Future[T], whenDuplicates: List[JobOverview] => Future[T], whenSingle: JobOverview => Future[T]): Future[T] = {
    val preparedName = modelData.objectNaming.prepareName(name.value, modelData.processConfig, new NamingContext(FlinkUsageKey))
    client.findJobsByName(preparedName).flatMap {
      case Nil => whenNone
      case duplicates if duplicates.count(isNotFinished) > 1 => whenDuplicates(duplicates)
      case jobs => whenSingle(findRunningOrFirst(jobs))
    }
  }

  private def findRunningOrFirst(jobOverviews: List[JobOverview]) = jobOverviews.find(isNotFinished).getOrElse(jobOverviews.head)

  //NOTE: Flink <1.10 compatibility - protected to make it easier to work with Flink 1.9, JobStatus changed package, so we use String in case class
  protected def isNotFinished(overview: JobOverview): Boolean = {
    !toJobStatus(overview).isGloballyTerminalState
  }

  private def toJobStatus(overview: JobOverview): JobStatus = {
    import org.apache.flink.api.common.JobStatus
    JobStatus.valueOf(overview.state)
  }

  //NOTE: Flink <1.10 compatibility - protected to make it easier to work with Flink 1.9, JobStatus changed package, so we use String in case class
  protected def mapJobStatus(overview: JobOverview): StateStatus = {
    toJobStatus(overview) match {
      case JobStatus.RUNNING if ensureTasksRunning(overview) => FlinkStateStatus.Running
      case s if checkDuringDeployForNotRunningJob(s) => FlinkStateStatus.DuringDeploy
      case JobStatus.FINISHED => FlinkStateStatus.Finished
      case JobStatus.RESTARTING => FlinkStateStatus.Restarting
      case JobStatus.CANCELED => FlinkStateStatus.Canceled
      case JobStatus.CANCELLING => FlinkStateStatus.DuringCancel
      //The job is not technically running, but should be in a moment
      case JobStatus.RECONCILING | JobStatus.CREATED | JobStatus.SUSPENDED => FlinkStateStatus.Running
      case JobStatus.FAILING => FlinkStateStatus.Failing
      case JobStatus.FAILED => FlinkStateStatus.Failed
    }

  }

  protected def ensureTasksRunning(overview: JobOverview): Boolean = {
    // We sum running and finished tasks because for batch jobs some tasks can be already finished but the others are still running.
    // We don't handle correctly case when job creates some tasks lazily e.g. in batch case. Without knowledge about what
    // kind of job is deployed, we don't know if it is such case or it is just a streaming job which is not fully running yet
    overview.tasks.running + overview.tasks.finished == overview.tasks.total
  }

  protected def checkDuringDeployForNotRunningJob(s: JobStatus): Boolean = {
    // Flink return running status even if some tasks are scheduled or initializing
    s == JobStatus.RUNNING || s == JobStatus.INITIALIZING
  }

  //TODO: cache by jobId?
  private def withVersion(jobId: String, name: ProcessName): Future[Option[ProcessVersion]] = {
    client.getJobConfig(jobId).map { executionConfig =>
      val userConfig = executionConfig.`user-config`
      for {
        version <- userConfig.get("versionId").flatMap(_.asString).map(_.toLong)
        user <- userConfig.get("user").map(_.asString.getOrElse(""))
        modelVersion = userConfig.get("modelVersion").flatMap(_.asString).map(_.toInt)
        processId = userConfig.get("processId").flatMap(_.asString).map(_.toLong).getOrElse(-1L)
      } yield {
        ProcessVersion(VersionId(version), name, ProcessId(processId), user, modelVersion)
      }
    }
  }

  override def cancel(processName: ProcessName, user: User): Future[Unit] = {
    def doCancel(overview: JobOverview) = {
      val status = mapJobStatus(overview)
      if (processStateDefinitionManager.statusActions(status).contains(ProcessActionType.Cancel) && isNotFinished(overview)) {
        cancel(ExternalDeploymentId(overview.jid))
      } else {
        logger.warn(s"Trying to cancel ${processName.value} which is in status $status.")
        Future.successful(())
      }
    }

    withJobOverview(processName)(
      whenNone = {
        logger.warn(s"Trying to cancel ${processName.value} which is not present in Flink.")
        Future.successful(())
      },
      whenDuplicates = { overviews =>
        logger.warn(s"Found duplicate jobs of ${processName.value}: $overviews. Cancelling all in non terminal state.")
        Future.sequence(overviews.map(doCancel)).map(_=> (()))
      },
      whenSingle = doCancel
    )
  }

  override protected def cancel(deploymentId: ExternalDeploymentId): Future[Unit] = {
    client.cancel(deploymentId)
  }

  override protected def makeSavepoint(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] = {
    client.makeSavepoint(deploymentId, savepointDir)
  }

  override protected def stop(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] = {
    client.stop(deploymentId, savepointDir)
  }

  // this code is executed synchronously by ManagementActor thus we don't care that much about possible races
  // and extraneous jar uploads introduced by asynchronous invocation
  override protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    logger.debug(s"Starting to deploy scenario: $processName with savepoint $savepointPath")
    client.runProgram(jarFile, mainClass, args, savepointPath)
  }

  override protected def checkRequiredSlotsExceedAvailableSlots(processDeploymentData: ProcessDeploymentData, currentlyDeployedJobId: Option[ExternalDeploymentId]): Future[Unit] = {
    slotsChecker.checkRequiredSlotsExceedAvailableSlots(processDeploymentData, currentlyDeployedJobId)
  }

  override def close(): Unit = Await.result(backend.close(), Duration(10, TimeUnit.SECONDS))

}
