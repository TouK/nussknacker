package pl.touk.nussknacker.engine.management

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.namespaces.{FlinkUsageKey, NamingContext}
import pl.touk.nussknacker.engine.api.process.ProcessName
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
    extends FlinkProcessManager(modelData, config.shouldVerifyBeforeDeploy.getOrElse(true), mainClassName) with LazyLogging {

  protected lazy val jarFile: File = new FlinkModelJar().buildJobJar(modelData)

  private val client = new HttpFlinkClient(config)

  /*
    It's ok to have many jobs with same name, however:
    - there MUST be at most 1 job in *non-terminal* state with given name
    - deployment is possible IFF there is NO job in *non-terminal* state with given name
   */
  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = {
    val preparedName = modelData.objectNaming.prepareName(name.value, modelData.processConfig, new NamingContext(FlinkUsageKey))

    client.findJobsByName(preparedName).flatMap {
      case Nil => Future.successful(None)
      case duplicates if duplicates.count(isNotFinished) > 1 =>
        Future.successful(Some(ProcessState(
          Some(DeploymentId(duplicates.head.jid)),
          //we cannot have e.g. Failed here as we don't want to allow more jobs
          FlinkStateStatus.MultipleJobsRunning,
          definitionManager = processStateDefinitionManager,
          version = Option.empty,
          attributes = Option.empty,
          startTime = Some(duplicates.head.`start-time`),
          errors = List(s"Expected one job, instead: ${duplicates.map(job => s"${job.jid} - ${job.state}").mkString(", ")}"))
        ))
      case jobs =>
        val job = findRunningOrFirst(jobs)
        val stateStatus = mapJobStatus(job)
        withVersion(job.jid, name).map { version =>
          //TODO: return error when there's no correct version in process
          //currently we're rather lax on this, so that this change is backward-compatible
          //we log debug here for now, since it's invoked v. often
          if (version.isEmpty) {
            logger.debug(s"No correct version in deployed process: ${job.name}")
          }

          Some(ProcessState(
            Some(DeploymentId(job.jid)),
            stateStatus,
            version = version,
            definitionManager = processStateDefinitionManager,
            startTime = Some(job.`start-time`),
            attributes = Option.empty,
            errors = List.empty
          ))
        }
    }
  }

  private def findRunningOrFirst(jobOverviews: List[JobOverview]) = jobOverviews.find(isNotFinished).getOrElse(jobOverviews.head)

  //NOTE: Flink <1.10 compatibility - protected to make it easier to work with Flink 1.9, JobStatus changed package, so we use String in case class
  protected def isNotFinished(overview: JobOverview): Boolean = {
    !org.apache.flink.api.common.JobStatus.valueOf(overview.state).isGloballyTerminalState
  }

  //NOTE: Flink <1.10 compatibility - protected to make it easier to work with Flink 1.9, JobStatus changed package, so we use String in case class
  protected def mapJobStatus(overview: JobOverview): StateStatus = {
    import org.apache.flink.api.common.JobStatus
    JobStatus.valueOf(overview.state) match {
      case JobStatus.RUNNING => FlinkStateStatus.Running
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

  //TODO: cache by jobId?
  private def withVersion(jobId: String, name: ProcessName): Future[Option[ProcessVersion]] = {
    client.getJobConfig(jobId).map { executionConfig =>
      val userConfig = executionConfig.`user-config`
      for {
        version <- userConfig.get("versionId").flatMap(_.asString).map(_.toLong)
        user <- userConfig.get("user").map(_.asString.getOrElse(""))
        modelVersion = userConfig.get("modelVersion").flatMap(_.asString).map(_.toInt)
      } yield {
        ProcessVersion(version, name, user, modelVersion)
      }
    }
  }


  override protected def cancel(deploymentId: DeploymentId): Future[Unit] = {
    client.cancel(deploymentId)
  }

  override protected def makeSavepoint(deploymentId: DeploymentId, savepointDir: Option[String]): Future[SavepointResult] = {
    client.makeSavepoint(deploymentId, savepointDir)
  }

  override protected def stop(deploymentId: DeploymentId, savepointDir: Option[String]): Future[SavepointResult] = {
    client.stop(deploymentId, savepointDir)
  }


  // this code is executed synchronously by ManagementActor thus we don't care that much about possible races
  // and extraneous jar uploads introduced by asynchronous invocation
  override protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Unit] = {
    logger.debug(s"Starting to deploy process: $processName with savepoint $savepointPath")
    client.runProgram(jarFile, mainClass, args, savepointPath)
  }

  override def close(): Unit = Await.result(backend.close(), Duration(10, TimeUnit.SECONDS))

}