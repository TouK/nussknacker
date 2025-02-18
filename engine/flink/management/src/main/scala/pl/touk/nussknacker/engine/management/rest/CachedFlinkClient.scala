package pl.touk.nussknacker.engine.management.rest

import com.github.benmanes.caffeine.cache.{AsyncCache, Cache, Caffeine}
import org.apache.flink.api.common.JobID
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.deployment.DataFreshnessPolicy.{CanBeCached, Fresh}
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, SavepointResult, WithDataFreshnessStatus}
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.{ExecutionConfig, JobDetails, JobOverview}

import java.io.File
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class CachedFlinkClient(delegate: FlinkClient, jobsOverviewCacheTTL: FiniteDuration, jobsConfigCacheSize: Int)
    extends FlinkClient {

  // In scala 2.12, Unit is not an AnyRef, so it is impossible to use it with buildAsync.
  // TODO: switch to Unit after migration to >= 2.13 only scala version(s)
  private val jobsOverviewCacheSingleKey = ""

  private val jobsOverviewCache: AsyncCache[String, List[JobOverview]] =
    Caffeine
      .newBuilder()
      .expireAfterWrite(java.time.Duration.ofMillis(jobsOverviewCacheTTL.toMillis))
      .buildAsync[String, List[JobOverview]]()

  private val jobsConfigCache: Cache[JobID, ExecutionConfig] =
    Caffeine
      .newBuilder()
      .maximumSize(jobsConfigCacheSize)
      .build[JobID, ExecutionConfig]()

  override def deleteJarIfExists(jarFileName: String): Future[Unit] =
    delegate.deleteJarIfExists(jarFileName)

  override def getJobsOverviews()(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[JobOverview]]] =
    freshnessPolicy match {
      case Fresh =>
        val resultFuture = delegate.getJobsOverviews()
        jobsOverviewCache.put(jobsOverviewCacheSingleKey, resultFuture.map(_.value).toJava.toCompletableFuture)
        resultFuture
      case CanBeCached =>
        Option(jobsOverviewCache.getIfPresent(jobsOverviewCacheSingleKey))
          .map(_.toScala.map(WithDataFreshnessStatus.cached))
          .getOrElse(
            jobsOverviewCache
              .get(
                jobsOverviewCacheSingleKey,
                (_, _) => delegate.getJobsOverviews().map(_.value).toJava.toCompletableFuture
              )
              .toScala
              .map(WithDataFreshnessStatus.fresh)
          )
    }

  override def getJobConfig(jobId: JobID): Future[ExecutionConfig] =
    Option(jobsConfigCache.getIfPresent(jobId))
      .map(Future.successful)
      .getOrElse(
        delegate.getJobConfig(jobId).map { jobConfig =>
          if (jobConfig.`user-config`.contains(CachedFlinkClient.DeploymentIdUserConfigKey)) {
            jobsConfigCache.put(jobId, jobConfig)
          }
          jobConfig
        }
      )

  override def getJobDetails(jobId: JobID): Future[Option[JobDetails]] = delegate.getJobDetails(jobId)

  override def cancel(jobId: JobID): Future[Unit] =
    delegate.cancel(jobId)

  override def makeSavepoint(
      jobId: JobID,
      savepointDir: Option[String]
  ): Future[SavepointResult] =
    delegate.makeSavepoint(jobId, savepointDir)

  override def stop(jobId: JobID, savepointDir: Option[String]): Future[SavepointResult] =
    delegate.stop(jobId, savepointDir)

  override def runProgram(
      jarFile: File,
      mainClass: String,
      args: List[String],
      savepointPath: Option[String],
      jobId: Option[JobID]
  ): Future[Option[JobID]] =
    delegate.runProgram(jarFile, mainClass, args, savepointPath, jobId)

  // TODO: Do we need cache here?
  override def getClusterOverview: Future[flinkRestModel.ClusterOverview] =
    delegate.getClusterOverview

  // TODO: Do we need cache here?
  override def getJobManagerConfig: Future[Configuration] =
    delegate.getJobManagerConfig

}

object CachedFlinkClient {

  val DeploymentIdUserConfigKey = "jobId"

}
