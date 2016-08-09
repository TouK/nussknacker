package pl.touk.esp.engine.management

import scala.concurrent.Future

trait ProcessManager {

  def deploy(processId: String, processAsJson: String) : Future[Unit]

  def findJobStatus(name: String) : Future[Option[JobState]]

  def cancel(name: String) : Future[Unit]

}
