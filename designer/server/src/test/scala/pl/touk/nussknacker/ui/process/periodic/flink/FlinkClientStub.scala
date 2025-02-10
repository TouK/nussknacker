package pl.touk.nussknacker.ui.process.periodic.flink

import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, SavepointResult, WithDataFreshnessStatus}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.management.rest.{FlinkClient, flinkRestModel}

import java.io.File
import scala.concurrent.Future

object FlinkClientStub extends FlinkClient {

  val maxParallelism    = 10
  val savepointPath     = "savepoints/123-savepoint"
  val stopSavepointPath = "savepoints/246-stop-savepoint"

  override def getJobsOverviews()(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[flinkRestModel.JobOverview]]] =
    Future.successful(WithDataFreshnessStatus.fresh(List.empty))

  override def getJobDetails(jobId: String): Future[Option[flinkRestModel.JobDetails]] = Future.successful(None)

  override def getJobConfig(jobId: String): Future[flinkRestModel.ExecutionConfig] =
    Future.successful(flinkRestModel.ExecutionConfig(1, Map.empty))

  override def cancel(deploymentId: ExternalDeploymentId): Future[Unit] = Future.successful(())

  override def makeSavepoint(
      deploymentId: ExternalDeploymentId,
      savepointDir: Option[String]
  ): Future[SavepointResult] = Future.successful(SavepointResult(savepointPath))

  override def stop(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] =
    Future.successful(SavepointResult(stopSavepointPath))

  override def runProgram(
      jarFile: File,
      mainClass: String,
      args: List[String],
      savepointPath: Option[String],
      jobId: Option[String]
  ): Future[Option[ExternalDeploymentId]] = Future.successful(None)

  override def deleteJarIfExists(jarFileName: String): Future[Unit] = Future.successful(())

  override def getClusterOverview: Future[flinkRestModel.ClusterOverview] =
    Future.successful(
      flinkRestModel.ClusterOverview(`slots-total` = maxParallelism, `slots-available` = maxParallelism)
    )

  override def getJobManagerConfig: Future[Configuration] = Future.successful(new Configuration)

}
