package pl.touk.nussknacker.engine.management.rest

import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, SavepointResult}
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.{JarFile, JobOverview}

import java.io.File
import scala.concurrent.Future

trait FlinkClient {
  
  def deleteJarIfExists(jarFileName: String): Future[Unit]

  def findJobsByName(jobName: String): Future[List[JobOverview]]

  def getJobConfig(jobId: String): Future[flinkRestModel.ExecutionConfig]

  def cancel(deploymentId: DeploymentId): Future[Unit]

  def makeSavepoint(deploymentId: DeploymentId, savepointDir: Option[String]): Future[SavepointResult]

  def stop(deploymentId: DeploymentId, savepointDir: Option[String]): Future[SavepointResult]

  def runProgram(jarFile: File,
                 mainClass: String,
                 args: List[String],
                 savepointPath: Option[String]): Future[Unit]
}

