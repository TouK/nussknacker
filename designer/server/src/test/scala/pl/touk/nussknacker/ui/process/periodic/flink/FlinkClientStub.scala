package pl.touk.nussknacker.ui.process.periodic.flink

import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, SavepointResult, WithDataFreshnessStatus}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.management.rest.{FlinkClient, flinkRestModel}

import java.io.File
import scala.concurrent.Future

class FlinkClientStub extends FlinkClient {

  override def getJobsOverviews()(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[flinkRestModel.JobOverview]]] = ???

  override def getJobDetails(jobId: String): Future[Option[flinkRestModel.JobDetails]] = ???

  override def getJobConfig(jobId: String): Future[flinkRestModel.ExecutionConfig] = ???

  override def cancel(deploymentId: ExternalDeploymentId): Future[Unit] = ???

  override def makeSavepoint(
      deploymentId: ExternalDeploymentId,
      savepointDir: Option[String]
  ): Future[SavepointResult] = ???

  override def stop(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] = ???

  override def runProgram(
      jarFile: File,
      mainClass: String,
      args: List[String],
      savepointPath: Option[String],
      jobId: Option[String]
  ): Future[Option[ExternalDeploymentId]] = ???

  override def deleteJarIfExists(jarFileName: String): Future[Unit] = Future.successful(())

  override def getClusterOverview: Future[flinkRestModel.ClusterOverview] = ???

  override def getJobManagerConfig: Future[Configuration] = ???

}
