package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateDefinitionDetails.UnknownIcon
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.testmode.TestProcess

import scala.concurrent.Future

class DeploymentManagerStub extends BaseDeploymentManager {

  var jobStatus: Option[ProcessState] = None

  def setStateStatus(status: StateStatus): Unit = {
    jobStatus = Some(ProcessState(
      deploymentId = Some(ExternalDeploymentId("1")),
      status = status,
      version = None,
      allowedActions = Nil,
      icon = UnknownIcon,
      tooltip = "dummy",
      description = "dummy",
      startTime = None,
      attributes = None,
      errors = Nil
    ))
  }


  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = ???

  override def cancel(name: ProcessName, user: User): Future[Unit] = Future.successful(())

  override def test[T](name: ProcessName, canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = ???

  override def getFreshProcessState(name: ProcessName): Future[Option[ProcessState]] = Future.successful(jobStatus)

  override def getFreshProcessState(name: ProcessName, lastAction: Option[ProcessAction]): Future[Option[ProcessState]] = Future.successful(jobStatus)

  override def validate(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess): Future[Unit] = Future.successful(())
}

