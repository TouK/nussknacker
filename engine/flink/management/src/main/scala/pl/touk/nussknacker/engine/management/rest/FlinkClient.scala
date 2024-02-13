package pl.touk.nussknacker.engine.management.rest

import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, SavepointResult, WithDataFreshnessStatus}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.{ClusterOverview, JobOverview}

import java.io.File
import scala.concurrent.Future

trait FlinkClient {

  def deleteJarIfExists(jarFileName: String): Future[Unit]

  def findJobsByName(jobName: String)(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[JobOverview]]]

  def getJobConfig(jobId: String): Future[flinkRestModel.ExecutionConfig]

  def cancel(deploymentId: ExternalDeploymentId): Future[Unit]

  def makeSavepoint(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult]

  def stop(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult]

  def getClusterOverview: Future[ClusterOverview]

  def getJobManagerConfig: Future[Configuration]

  def runProgram(
      jarFile: File,
      mainClass: String,
      args: List[String],
      savepointPath: Option[String]
  ): Future[Option[ExternalDeploymentId]]

}
