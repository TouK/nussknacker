package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.process.ProcessName

import scala.concurrent.Future

trait ProcessManager extends AutoCloseable {

  //TODO: savepointPath is very flink specific, how can we handle that differently?
  def deploy(processId: ProcessVersion, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String], user: User) : Future[Unit]

  def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult]

  def cancel(name: ProcessName, user: User) : Future[Unit]

  def test[T](name: ProcessName, json: String, testData: TestData, variableEncoder: Any => T): Future[TestResults[T]]

  def findJobStatus(name: ProcessName) : Future[Option[ProcessState]]

  //TODO: this is very flink specific, how can we handle that differently?
  def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult]

  def processStateDefinitionManager: ProcessStateDefinitionManager

  /*
  TODO:
    1. Action params
    2. ProcessStateDefinitionManager should define allowed custom actions based on a current process state
   */
  def customAction(customAction: CustomActionReq): Future[Either[CustomActionErr, CustomActionRes]]
}
