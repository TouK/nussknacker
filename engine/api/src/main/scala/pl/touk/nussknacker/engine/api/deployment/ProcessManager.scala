package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.test.TestResultsEncoded

import scala.concurrent.Future

trait ProcessManager {

  //TODO: savepointPath is very flink specific, how can we handle that differently?
  def deploy(processId: String, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String]) : Future[Unit]

  def test[T](processId: String, json: String, testData: TestData, encoder: TestResults => T): Future[TestResultsEncoded[T]]

  def findJobStatus(name: String) : Future[Option[ProcessState]]

  //TODO: this is very flink specific, how can we handle that differently?
  def savepoint(processId: String, savepointDir: String): Future[String]

  def cancel(name: String) : Future[Unit]

}