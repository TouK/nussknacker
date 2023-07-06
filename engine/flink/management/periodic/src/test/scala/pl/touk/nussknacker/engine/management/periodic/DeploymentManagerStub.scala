package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.testmode.TestProcess

import java.time.Instant
import scala.concurrent.Future

class DeploymentManagerStub extends BaseDeploymentManager with PostprocessingProcessStatus {

  var jobStatus: Option[StatusDetails] = None

  def setStateStatus(status: StateStatus): Unit = {
    jobStatus = Some(StatusDetails(
      deploymentId = Some(ExternalDeploymentId("1")),
      status = status,
      version = None,
      startTime = None,
      attributes = None,
      errors = Nil
    ))
  }


  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = ???

  override def cancel(name: ProcessName, user: User): Future[Unit] = Future.successful(())

  override def test[T](name: ProcessName, canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = ???

  override def getProcessState(name: ProcessName, lastStateAction: Option[ProcessAction])(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[ProcessState]] =
    Future.successful(WithDataFreshnessStatus(processStateDefinitionManager.processState(jobStatus.getOrElse(StatusDetails(SimpleStateStatus.NotDeployed))), cached = false))

  override def postprocess(name: ProcessName, statusDetailsOpt: Option[StatusDetails]): Future[Option[ProcessAction]] =
    Future.successful(
      for {
        statusDetails <- statusDetailsOpt
        if statusDetails.status == SimpleStateStatus.Finished
      } yield ProcessAction(VersionId(-123), Instant.ofEpochMilli(0), "fooUser", ProcessActionType.Cancel, None, None, Map.empty)
    )

  override def getFreshProcessState(name: ProcessName): Future[Option[StatusDetails]] = Future.successful(jobStatus)

  override def validate(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess): Future[Unit] = Future.successful(())
}

