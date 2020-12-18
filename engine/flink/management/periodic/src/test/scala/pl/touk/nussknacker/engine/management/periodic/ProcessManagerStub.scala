package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.FlinkProcessStateDefinitionManager

import scala.concurrent.Future

class ProcessManagerStub extends ProcessManager {

  var jobStatus: Option[ProcessState] = None

  def setStateStatus(status: StateStatus): Unit = {
    jobStatus = Some(ProcessState(
      deploymentId = DeploymentId("1"),
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

  override def deploy(processId: ProcessVersion, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String], user: User): Future[Unit] = Future.successful(())

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] = ???

  override def cancel(name: ProcessName, user: User): Future[Unit] = Future.successful(())

  override def test[T](name: ProcessName, json: String, testData: TestProcess.TestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = ???

  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = Future.successful(jobStatus)

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] = ???

  override def processStateDefinitionManager: ProcessStateDefinitionManager = FlinkProcessStateDefinitionManager

  override def close(): Unit = ???
}

