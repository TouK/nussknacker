package pl.touk.nussknacker.engine.management.rest

import com.github.benmanes.caffeine.cache.{AsyncCache, Caffeine}
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.deployment.DataFreshnessPolicy.{CanBeCached, Fresh}
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, SavepointResult, WithDataFreshnessStatus}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.{ExecutionConfig, JobOverview}

import java.io.File
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class CachedFlinkClient(delegate: FlinkClient, jobsOverviewCacheTTL: FiniteDuration, jobsConfigCacheSize: Int)
    extends FlinkClient {

  private val jobsOverviewCache: AsyncCache[String, List[JobOverview]] =
    Caffeine
      .newBuilder()
      .expireAfterWrite(java.time.Duration.ofMillis(jobsOverviewCacheTTL.toMillis))
      .buildAsync[String, List[JobOverview]]()

  private val jobsConfigCache: AsyncCache[String, ExecutionConfig] =
    Caffeine
      .newBuilder()
      .maximumSize(jobsConfigCacheSize)
      .buildAsync[String, ExecutionConfig]()

  override def deleteJarIfExists(jarFileName: String): Future[Unit] =
    delegate.deleteJarIfExists(jarFileName)

  override def findJobsByName(
      jobName: String
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[JobOverview]]] =
    freshnessPolicy match {
      case Fresh =>
        val resultFuture = delegate.findJobsByName(jobName)
        jobsOverviewCache.put(jobName, resultFuture.map(_.value).toJava.toCompletableFuture)
        resultFuture
      case CanBeCached =>
        Option(jobsOverviewCache.getIfPresent(jobName))
          .map(_.toScala.map(WithDataFreshnessStatus.cached))
          .getOrElse(
            jobsOverviewCache
              .get(jobName, (_, _) => delegate.findJobsByName(jobName).map(_.value).toJava.toCompletableFuture)
              .toScala
              .map(WithDataFreshnessStatus.fresh)
          )
    }

  override def getJobConfig(jobId: String): Future[ExecutionConfig] =
    jobsConfigCache
      .get(jobId, (_, _) => delegate.getJobConfig(jobId).toJava.toCompletableFuture)
      .toScala

  override def cancel(deploymentId: ExternalDeploymentId): Future[Unit] =
    delegate.cancel(deploymentId)

  override def makeSavepoint(
      deploymentId: ExternalDeploymentId,
      savepointDir: Option[String]
  ): Future[SavepointResult] =
    delegate.makeSavepoint(deploymentId, savepointDir)

  override def stop(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] =
    delegate.stop(deploymentId, savepointDir)

  override def runProgram(
      jarFile: File,
      mainClass: String,
      args: List[String],
      savepointPath: Option[String]
  ): Future[Option[ExternalDeploymentId]] =
    delegate.runProgram(jarFile, mainClass, args, savepointPath)

  // TODO: Do we need cache here?
  override def getClusterOverview: Future[flinkRestModel.ClusterOverview] =
    delegate.getClusterOverview

  // TODO: Do we need cache here?
  override def getJobManagerConfig: Future[Configuration] =
    delegate.getJobManagerConfig

}
