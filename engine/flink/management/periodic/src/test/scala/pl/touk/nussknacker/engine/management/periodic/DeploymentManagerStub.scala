package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.testmode.TestProcess

import java.time.Instant
import java.util.UUID
import scala.concurrent.Future

class DeploymentManagerStub extends BaseDeploymentManager with PostprocessingProcessStatus {

  var jobStatus: Option[StatusDetails] = None

  def setStateStatus(status: StateStatus): Unit = {
    jobStatus = Some(StatusDetails(
      deploymentId = None,
      externalDeploymentId = Some(ExternalDeploymentId("1")),
      status = status,
      version = None,
      startTime = None,
      attributes = None,
      errors = Nil
    ))
  }


  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = ???

  override def cancel(name: ProcessName, user: User): Future[Unit] = Future.successful(())

  override def cancel(name: ProcessName, deploymentId: DeploymentId, user: User): Future[Unit] = Future.successful(())

  override def test[T](name: ProcessName, canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = ???

  override def getProcessState(idWithName: ProcessIdWithName, lastStateAction: Option[ProcessAction])(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[ProcessState]] =
    Future.successful(WithDataFreshnessStatus(processStateDefinitionManager.processState(jobStatus.getOrElse(StatusDetails(SimpleStateStatus.NotDeployed, None))), cached = false))

  override def getFreshProcessStates(name: ProcessName): Future[List[StatusDetails]] = Future.successful(jobStatus.toList)

  override def postprocess(idWithName: ProcessIdWithName, statusDetailsList: List[StatusDetails]): Future[Option[ProcessAction]] =
    Future.successful(
      statusDetailsList
        .find(_.status == SimpleStateStatus.Finished)
        .map(_ => ProcessAction(
          id = ProcessActionId(UUID.randomUUID()),
          processId = ProcessId(-234),
          processVersionId = VersionId(-123),
          user = "fooUser",
          createdAt = Instant.ofEpochMilli(0),
          performedAt = Instant.ofEpochMilli(0),
          actionType = ProcessActionType.Cancel,
          state = ProcessActionState.Finished,
          failureMessage = None,
          commentId = None,
          comment = None,
          buildInfo = Map.empty))
    )

  override def validate(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess): Future[Unit] = Future.successful(())
}

