package pl.touk.nussknacker.engine.management

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobStatus
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{DeploymentStatus, StatusDetails}
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.FlinkStatusDetailsDeterminer.{ParsedJobConfig, toDeploymentStatus}
import pl.touk.nussknacker.engine.management.rest.flinkRestModel
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.{BaseJobStatusCounts, JobOverview}

import scala.concurrent.{ExecutionContext, Future}

class FlinkStatusDetailsDeterminer(
    namingStrategy: NamingStrategy,
    getJobConfig: String => Future[flinkRestModel.ExecutionConfig]
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  def statusDetailsFromJobOverviews(jobOverviews: List[JobOverview]): Future[Map[ProcessName, List[StatusDetails]]] =
    Future
      .sequence {
        jobOverviews
          .groupBy(_.name)
          .flatMap { case (name, jobs) =>
            namingStrategy.decodeName(name).map(decoded => (ProcessName(decoded), jobs))
          }
          .map { case (name, jobs) =>
            val statusDetails = jobs.map { job =>
              withParsedJobConfig(job.jid, name).map { jobConfig =>
                // TODO: return error when there's no correct version in process
                // currently we're rather lax on this, so that this change is backward-compatible
                // we log debug here for now, since it's invoked v. often
                if (jobConfig.isEmpty) {
                  logger.debug(s"No correct job details in deployed scenario: ${job.name}")
                }
                StatusDetails(
                  SimpleStateStatus.fromDeploymentStatus(toDeploymentStatus(JobStatus.valueOf(job.state), job.tasks)),
                  jobConfig.flatMap(_.deploymentId),
                  Some(ExternalDeploymentId(job.jid)),
                  version = jobConfig.map(_.version),
                  startTime = Some(job.`start-time`),
                  attributes = Option.empty,
                  errors = List.empty
                )
              }
            }
            Future.sequence(statusDetails).map((name, _))
          }

      }
      .map(_.toMap)

  private def withParsedJobConfig(jobId: String, name: ProcessName): Future[Option[ParsedJobConfig]] = {
    getJobConfig(jobId).map { executionConfig =>
      val userConfig = executionConfig.`user-config`
      for {
        version <- userConfig.get("versionId").flatMap(_.asString).map(_.toLong).map(VersionId(_))
        user    <- userConfig.get("user").map(_.asString.getOrElse(""))
        modelVersion = userConfig.get("modelVersion").flatMap(_.asString).map(_.toInt)
        processId    = ProcessId(userConfig.get("processId").flatMap(_.asString).map(_.toLong).getOrElse(-1L))
        labels       = userConfig.get("labels").flatMap(_.asArray).map(_.toList.flatMap(_.asString)).toList.flatten
        deploymentId = userConfig.get("deploymentId").flatMap(_.asString).map(DeploymentId(_))
      } yield {
        val versionDetails = ProcessVersion(version, name, processId, labels, user, modelVersion)
        ParsedJobConfig(versionDetails, deploymentId)
      }
    }
  }

}

object FlinkStatusDetailsDeterminer {

  // TODO: deploymentId is optional to handle situation when on Flink there is old version of runtime and in designer is the new one.
  //       After fully deploy of new version it should be mandatory
  private case class ParsedJobConfig(version: ProcessVersion, deploymentId: Option[DeploymentId])

  private[management] def toDeploymentStatus(
      jobStatus: JobStatus,
      jobStatusCounts: BaseJobStatusCounts
  ): DeploymentStatus = {
    jobStatus match {
      case JobStatus.RUNNING if ensureTasksRunning(jobStatusCounts)       => DeploymentStatus.Running
      case JobStatus.RUNNING | JobStatus.INITIALIZING | JobStatus.CREATED => DeploymentStatus.DuringDeploy
      case JobStatus.FINISHED                                             => DeploymentStatus.Finished
      case JobStatus.RESTARTING                                           => DeploymentStatus.Restarting
      case JobStatus.CANCELED                                             => DeploymentStatus.Canceled
      case JobStatus.CANCELLING                                           => DeploymentStatus.DuringCancel
      // The job is not technically running, but should be in a moment
      case JobStatus.RECONCILING | JobStatus.SUSPENDED => DeploymentStatus.Running
      case JobStatus.FAILING | JobStatus.FAILED =>
        DeploymentStatus.Problem.Failed // redeploy allowed, handle with restartStrategy
    }
  }

  private def ensureTasksRunning(jobStatusCount: BaseJobStatusCounts): Boolean = {
    // We sum running and finished tasks because for batch jobs some tasks can be already finished but the others are still running.
    // We don't handle correctly case when job creates some tasks lazily e.g. in batch case. Without knowledge about what
    // kind of job is deployed, we don't know if it is such case or it is just a streaming job which is not fully running yet
    jobStatusCount.running + jobStatusCount.finished == jobStatusCount.total
  }

}
