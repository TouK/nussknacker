package pl.touk.nussknacker.engine.management

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobID, JobStatus}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentStatus, DeploymentStatusDetails}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.deployment.DeploymentId
import pl.touk.nussknacker.engine.management.FlinkStatusDetailsDeterminer.{toDeploymentStatus, ParsedJobConfig}
import pl.touk.nussknacker.engine.management.rest.flinkRestModel
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.{BaseJobStatusCounts, JobOverview}
import pl.touk.nussknacker.engine.util.Implicits.RichTupleList

import scala.concurrent.{ExecutionContext, Future}

class FlinkStatusDetailsDeterminer(
    namingStrategy: NamingStrategy,
    getJobConfig: JobID => Future[flinkRestModel.ExecutionConfig]
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  def statusDetailsFromJobOverviews(
      jobOverviews: List[JobOverview]
  ): Future[Map[ProcessName, List[(DeploymentStatusDetails, JobOverview)]]] =
    Future
      .sequence {
        for {
          job  <- jobOverviews
          name <- namingStrategy.decodeName(job.name).map(ProcessName(_))
        } yield withParsedJobConfig(job.jid, name).map { jobConfigOpt =>
          val details = jobConfigOpt.map { jobConfig =>
            DeploymentStatusDetails(
              status =
                SimpleStateStatus.fromDeploymentStatus(toDeploymentStatus(JobStatus.valueOf(job.state), job.tasks)),
              deploymentId = jobConfig.deploymentId,
              version = Some(jobConfig.version)
            )
          } getOrElse {
            logger.debug(
              s"No correct job config in deployed scenario: $name. Returning ${SimpleStateStatus.DuringDeploy} without version"
            )
            DeploymentStatusDetails(
              SimpleStateStatus.DuringDeploy,
              // For scheduling mechanism this fallback is probably wrong // TODO: switch scheduling mechanism deployment ids to UUIDs
              Some(DeploymentId(job.jid.toHexString)),
              version = None,
            )
          }
          name -> (details, job)
        }
      }
      .map(_.toGroupedMap)

  private def withParsedJobConfig(jobId: JobID, name: ProcessName): Future[Option[ParsedJobConfig]] = {
    getJobConfig(jobId).map { executionConfig =>
      val userConfig = executionConfig.`user-config`
      for {
        version <- userConfig.get("versionId").flatMap(_.asString).map(_.toLong).map(VersionId(_))
        deploymentId = userConfig.get("deploymentId").flatMap(_.asString).map(DeploymentId(_))
      } yield {
        ParsedJobConfig(version, deploymentId)
      }
    }
  }

}

object FlinkStatusDetailsDeterminer {

  // TODO: deploymentId is optional to handle situation when on Flink there is old version of runtime and in designer is the new one.
  //       After fully deploy of new version it should be mandatory
  private case class ParsedJobConfig(version: VersionId, deploymentId: Option[DeploymentId])

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
