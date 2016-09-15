package pl.touk.esp.engine.api.deployment

import scala.concurrent.Future

trait ProcessManager {

  def deploy(processId: String, processDeploymentData: ProcessDeploymentData) : Future[Unit]

  def findJobStatus(name: String) : Future[Option[ProcessState]]

  def cancel(name: String) : Future[Unit]

}
