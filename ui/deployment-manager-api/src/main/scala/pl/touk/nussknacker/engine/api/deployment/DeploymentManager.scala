package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.process.ProcessName

import scala.concurrent.Future

trait DeploymentManager extends AutoCloseable {

  //TODO: savepointPath is very flink specific, how can we handle that differently?
  def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, graphProcess: GraphProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]]

  def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult]

  def cancel(name: ProcessName, user: User): Future[Unit]

  def test[T](name: ProcessName, graphProcess: GraphProcess, testData: TestData, variableEncoder: Any => T): Future[TestResults[T]]

  def findJobStatus(name: ProcessName): Future[Option[ProcessState]]

  //TODO: this is very flink specific, how can we handle that differently?
  def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult]

  def processStateDefinitionManager: ProcessStateDefinitionManager

  def customActions: List[CustomAction]

  def invokeCustomAction(actionRequest: CustomActionRequest, graphProcess: GraphProcess): Future[Either[CustomActionError, CustomActionResult]]
}
