package pl.touk.esp.engine.api.deployment

import pl.touk.esp.engine.api.deployment.test.{TestData, TestResults}

import scala.concurrent.Future

trait ProcessManager {

  def deploy(processId: String, processDeploymentData: ProcessDeploymentData) : Future[Unit]

  def test(processId: String, json: String, testData: TestData): Future[TestResults]

  def findJobStatus(name: String) : Future[Option[ProcessState]]

  def cancel(name: String) : Future[Unit]

}
