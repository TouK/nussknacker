package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, TestData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId, User}

import scala.concurrent.Future

trait DeploymentManager extends AutoCloseable {

  /**
    * This method is invoked separately before deploy, to be able to give user quick feedback, as deploy (e.g. on Flink) may take long time
    */
  def validate(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess): Future[Unit]

  //TODO: savepointPath is very flink specific, we should handle this mode via custom action
  /**
    * We assume that validate was already called and was successful
    *  */
  def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]]

  def cancel(name: ProcessName, user: User): Future[Unit]

  def test[T](name: ProcessName, canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData, variableEncoder: Any => T): Future[TestResults[T]]

  def findJobStatus(name: ProcessName): Future[Option[ProcessState]]

  def processStateDefinitionManager: ProcessStateDefinitionManager

  def customActions: List[CustomAction]

  def invokeCustomAction(actionRequest: CustomActionRequest, canonicalProcess: CanonicalProcess): Future[Either[CustomActionError, CustomActionResult]]

  //TODO: this is very flink specific, we should handle it via custom action
  def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult]

  //TODO: savepointPath is very flink specific, we should handle it via custom action
  def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult]

}
