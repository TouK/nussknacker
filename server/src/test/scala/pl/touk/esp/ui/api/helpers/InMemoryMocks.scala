package pl.touk.esp.ui.api.helpers

import pl.touk.esp.engine.api.deployment.{ProcessDeploymentData, ProcessManager, ProcessState}

import scala.concurrent.Future

object InMemoryMocks {

  val mockProcessManager = new ProcessManager {
    override def findJobStatus(name: String): Future[Option[ProcessState]] = Future.successful(None)
    override def cancel(name: String): Future[Unit] = Future.successful(Unit)
    override def deploy(processId: String, processDeploymentData: ProcessDeploymentData): Future[Unit] = Future.successful(Unit)
  }

}
