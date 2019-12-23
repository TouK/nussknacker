package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StateStatus
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.process.ProcessName

import scala.concurrent.Future

trait ProcessManager {

  //TODO: savepointPath is very flink specific, how can we handle that differently?
  def deploy(processId: ProcessVersion, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String]) : Future[Unit]

  def test[T](name: ProcessName, json: String, testData: TestData, variableEncoder: Any => T): Future[TestResults[T]]

  def findJobStatus(name: ProcessName) : Future[Option[ProcessState]]

  //TODO: this is very flink specific, how can we handle that differently?
  def savepoint(name: ProcessName, savepointDir: String): Future[String]

  def cancel(name: ProcessName) : Future[Unit]

  def processStateConfigurator: ProcessStateConfigurator

}
