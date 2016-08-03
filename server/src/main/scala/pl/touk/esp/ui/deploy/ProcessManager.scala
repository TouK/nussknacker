package pl.touk.esp.ui.deploy

import com.typesafe.config.Config
import org.apache.flink.runtime.client.JobStatusMessage
import pl.touk.esp.engine.graph.EspProcess

import scala.concurrent.Future

trait ProcessManager {

  def deploy(processId: String, processAsJson: String) : Future[Unit]

  def findJobStatus(name: String) : Future[Option[JobState]]

  def cancel(name: String) : Future[Unit]

}
