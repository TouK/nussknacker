package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction

import scala.concurrent.Future

object InvalidDeploymentManagerStub extends DeploymentManager {

  private val stubbedActionResponse =
    Future.failed(new ProcessIllegalAction("Can't perform action because of an error in deployment configuration"))

  private val stubbedStatus = StatusDetails(
    ProblemStateStatus("Error in deployment configuration", allowedActions = List.empty),
    deploymentId = None
  )

  override def getProcessStates(name: ProcessName)(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    Future.successful(WithDataFreshnessStatus.fresh(List(stubbedStatus)))
  }

  override def resolve(
      idWithName: ProcessIdWithName,
      statusDetails: List[StatusDetails],
      lastStateAction: Option[ProcessAction]
  ): Future[ProcessState] = {
    Future.successful(processStateDefinitionManager.processState(stubbedStatus))
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def validate(
      processVersion: ProcessVersion,
      deploymentData: deployment.DeploymentData,
      canonicalProcess: CanonicalProcess
  ): Future[Unit] =
    Future.unit

  override def deploy(
      processVersion: ProcessVersion,
      deploymentData: deployment.DeploymentData,
      canonicalProcess: CanonicalProcess,
      savepointPath: Option[String]
  ): Future[Option[ExternalDeploymentId]] =
    stubbedActionResponse

  override def cancel(name: ProcessName, user: User): Future[Unit] = stubbedActionResponse

  override def cancel(name: ProcessName, deploymentId: DeploymentId, user: User): Future[Unit] = stubbedActionResponse

  override def test(
      name: ProcessName,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData
  ): Future[TestProcess.TestResults] = stubbedActionResponse

  override def customActions: List[CustomAction] = List.empty

  override def invokeCustomAction(
      actionRequest: ActionRequest,
      canonicalProcess: CanonicalProcess
  ): Future[ActionResult] = stubbedActionResponse

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] =
    stubbedActionResponse

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] =
    stubbedActionResponse

  override def stop(
      name: ProcessName,
      deploymentId: DeploymentId,
      savepointDir: Option[String],
      user: User
  ): Future[SavepointResult] = stubbedActionResponse

  override def close(): Unit = ()
}
