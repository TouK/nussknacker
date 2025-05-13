package pl.touk.nussknacker.engine.management.rest

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobID
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, SavepointResult, WithDataFreshnessStatus}
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.{ClusterOverview, JobDetails, JobOverview}
import sttp.client3.SttpBackend

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

trait FlinkClient {

  def deleteJarIfExists(jarFileName: String): Future[Unit]

  def getJobsOverviews()(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[JobOverview]]]

  def getJobDetails(jobId: JobID): Future[Option[JobDetails]]

  def getJobConfig(jobId: JobID): Future[flinkRestModel.ExecutionConfig]

  def cancel(jobId: JobID): Future[Unit]

  def makeSavepoint(jobId: JobID, savepointDir: Option[String]): Future[SavepointResult]

  def stop(jobId: JobID, savepointDir: Option[String]): Future[SavepointResult]

  def getClusterOverview: Future[ClusterOverview]

  def getJobManagerConfig: Future[Configuration]

  def runProgram(
      jarFile: File,
      mainClass: String,
      args: List[String],
      savepointPath: Option[String],
      jobId: Option[JobID]
  ): Future[Option[JobID]]

}

object FlinkClient extends LazyLogging {

  def create(
      config: ParsedHttpFlinkClientConfig
  )(implicit backend: SttpBackend[Future, Any], ec: ExecutionContext): FlinkClient = {
    val httpClient = new HttpFlinkClient(
      restUrl = config.restUrl,
      scenarioStateRequestTimeout = config.scenarioStateRequestTimeout,
      jobManagerTimeout = config.jobManagerTimeout
    )
    config.scenarioStateCacheTTL
      .map { cacheTTL =>
        logger.debug(s"Wrapping FlinkRestManager's client: $httpClient with caching mechanism with TTL: $cacheTTL")
        new CachedFlinkClient(httpClient, cacheTTL, config.jobConfigsCacheSize)
      }
      .getOrElse {
        logger.debug(s"Skipping caching for FlinkRestManager's client: $httpClient")
        httpClient
      }
  }

}
