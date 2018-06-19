package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}

import scala.concurrent.Future

trait ProcessManager {

  //TODO: savepointPath is very flink specific, how can we handle that differently?
  def deploy(processId: ProcessVersion, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String]) : Future[Unit]

  def test[T](processId: String, json: String, testData: TestData, variableEncoder: Any => T): Future[TestResults[T]]

  def findJobStatus(name: String) : Future[Option[ProcessState]]

  //TODO: this is very flink specific, how can we handle that differently?
  def savepoint(processId: String, savepointDir: String): Future[String]

  def cancel(name: String) : Future[Unit]

}