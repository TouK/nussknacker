package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName

import scala.concurrent.Future

class DeploymentManagerStub extends BaseDeploymentManager {

  var jobStatus: Option[ProcessState] = None

  def setStateStatus(status: StateStatus): Unit = {
    jobStatus = Some(ProcessState(
      deploymentId = Some(ExternalDeploymentId("1")),
      status = status,
      version = None,
      allowedActions = Nil,
      icon = None,
      tooltip = None,
      description = None,
      startTime = None,
      attributes = None,
      errors = Nil
    ))
  }


  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, graphProcess: GraphProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = ???

  override def cancel(name: ProcessName, user: User): Future[Unit] = Future.successful(())

  override def test[T](name: ProcessName, json: String, testData: TestProcess.TestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = ???

  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = Future.successful(jobStatus)

}

