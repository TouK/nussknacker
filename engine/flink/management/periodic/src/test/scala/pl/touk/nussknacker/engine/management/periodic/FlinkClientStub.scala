package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ExternalDeploymentId, SavepointResult}
import pl.touk.nussknacker.engine.management.rest.{FlinkClient, flinkRestModel}

import java.io.File
import scala.concurrent.Future

class FlinkClientStub extends FlinkClient {

  override def findJobsByName(jobName: String): Future[List[flinkRestModel.JobOverview]] = ???

  override def getJobConfig(jobId: String): Future[flinkRestModel.ExecutionConfig] = ???

  override def cancel(deploymentId: ExternalDeploymentId): Future[Unit] = ???

  override def makeSavepoint(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] = ???

  override def stop(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] = ???

  override def runProgram(jarFile: File, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Unit] = ???

  override def deleteJarIfExists(jarFileName: String): Future[Unit] = Future.successful(())
}
