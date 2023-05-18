package pl.touk.nussknacker.engine.management

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
import pl.touk.nussknacker.engine.deployment.{ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.management.rest.HttpFlinkClient
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.JobOverview
import sttp.client3._

import java.io.File
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class FlinkRestManager(config: FlinkConfig, modelData: BaseModelData, mainClassName: String)
                      (implicit ec: ExecutionContext, backend: SttpBackend[Future, Any])
    extends FlinkDeploymentManager(modelData, config.shouldVerifyBeforeDeploy, mainClassName) with LazyLogging {

  protected lazy val jarFile: File = new FlinkModelJar().buildJobJar(modelData)

  private val client = new HttpFlinkClient(config)

  private val slotsChecker = new FlinkSlotsChecker(client)

  /*
  It's ok to have many jobs with same name, however:
  - there MUST be at most 1 job in *non-terminal* state with given name
  - deployment is possible IFF there is NO job in *non-terminal* state with given name
 */
  override def getFreshProcessState(name: ProcessName): Future[Option[ProcessState]] = withJobOverview(name)(
    whenNone = Future.successful(None),
    //we cannot have e.g. Failed here as we don't want to allow more jobs
    whenDuplicates = duplicates => Future.successful(Some(
      FlinkProcessStateDefinitionManager.errorMultipleJobsRunning(duplicates)
    )),
    whenSingle = job => withVersion(job.jid, name).map { version =>
      //TODO: return error when there's no correct version in process
      //currently we're rather lax on this, so that this change is backward-compatible
      //we log debug here for now, since it's invoked v. often
      if (version.isEmpty) {
        logger.debug(s"No correct version in deployed scenario: ${job.name}")
      }
      Some(processStateDefinitionManager.processState(
        mapJobStatus(job),
        Some(ExternalDeploymentId(job.jid)),
        version = version,
        startTime = Some(job.`start-time`),
        attributes = Option.empty,
        errors = List.empty
      ))
    }
  )

  // FIXME
  override protected def getFreshProcessState(name: ProcessName, lastAction: Option[ProcessAction]): Future[Option[ProcessState]] = ???

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
      case JobStatus.RUNNING if ensureTasksRunning(overview) => SimpleStateStatus.Running
      case s if checkDuringDeployForNotRunningJob(s) => SimpleStateStatus.DuringDeploy
      case JobStatus.FINISHED => SimpleStateStatus.Finished
      case JobStatus.RESTARTING => SimpleStateStatus.Restarting
      case JobStatus.CANCELED => SimpleStateStatus.Canceled
      case JobStatus.CANCELLING => SimpleStateStatus.DuringCancel
      //The job is not technically running, but should be in a moment
      case JobStatus.RECONCILING | JobStatus.CREATED | JobStatus.SUSPENDED => SimpleStateStatus.Running
      case JobStatus.FAILING => ProblemStateStatus.failed // redeploy allowed, handle with restartStrategy
      case JobStatus.FAILED => ProblemStateStatus.failed // redeploy allowed, handle with restartStrategy
      case _ => throw new IllegalStateException() // todo: drop support for Flink 1.11 & inline `checkDuringDeployForNotRunningJob` so we could benefit from pattern matching exhaustive check
    }

  }

  protected def ensureTasksRunning(overview: JobOverview): Boolean = {
    // We sum running and finished tasks because for batch jobs some tasks can be already finished but the others are still running.
    // We don't handle correctly case when job creates some tasks lazily e.g. in batch case. Without knowledge about what
    // kind of job is deployed, we don't know if it is such case or it is just a streaming job which is not fully running yet
    overview.tasks.running + overview.tasks.finished == overview.tasks.total
  }

  // todo: drop support for Flink 1.11 & inline `checkDuringDeployForNotRunningJob` so we could benefit from pattern matching exhaustive check
  protected def checkDuringDeployForNotRunningJob(s: JobStatus): Boolean = {
    // Flink return running status even if some tasks are scheduled or initializing
    s == JobStatus.RUNNING || s == JobStatus.INITIALIZING
  }

  override protected def waitForDuringDeployFinished(processName: ProcessName): Future[Unit] = {
    config.waitForDuringDeployFinish.toEnabledConfig.map { config =>
      retry.Pause(config.maxChecks, config.delay).apply {
        getFreshProcessState(processName).map {
          case Some(state) if state.status.isDuringDeploy => Left(())
          case _ => Right(())
        }
      }.map(_.getOrElse(throw new IllegalStateException("Deploy execution finished, but job is still in during deploy state on Flink")))
    }.getOrElse(Future.successful(()))
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

  override protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    logger.debug(s"Starting to deploy scenario: $processName with savepoint $savepointPath")
    client.runProgram(jarFile, mainClass, args, savepointPath)
  }

  override protected def checkRequiredSlotsExceedAvailableSlots(canonicalProcess: CanonicalProcess, currentlyDeployedJobId: Option[ExternalDeploymentId]): Future[Unit] = {
    if (config.shouldCheckAvailableSlots) {
      slotsChecker.checkRequiredSlotsExceedAvailableSlots(canonicalProcess, currentlyDeployedJobId)
    } else
      Future.successful(())
  }

  override def close(): Unit = Await.result(backend.close(), Duration(10, TimeUnit.SECONDS))

}
