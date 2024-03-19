package pl.touk.nussknacker.engine.management.periodic

import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentId
import pl.touk.nussknacker.engine.testmode.TestProcess

import scala.concurrent.Future

class DeploymentManagerStub extends BaseDeploymentManager {

  var jobStatus: Option[StatusDetails] = None

  def setStateStatus(status: StateStatus, deploymentIdOpt: Option[PeriodicProcessDeploymentId]): Unit = {
    jobStatus = Some(
      StatusDetails(
        deploymentId = deploymentIdOpt.map(pdid => DeploymentId(pdid.toString)),
        externalDeploymentId = Some(ExternalDeploymentId("1")),
        status = status,
        version = None,
        startTime = None,
        attributes = None,
        errors = Nil
      )
    )
  }

  override def deploy(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess,
      savepointPath: Option[String]
  ): Future[Option[ExternalDeploymentId]] = ???

  override def cancel(name: ProcessName, user: User): Future[Unit] = Future.successful(())

  override def cancel(name: ProcessName, deploymentId: DeploymentId, user: User): Future[Unit] = Future.successful(())

  override def test(
      name: ProcessName,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
  ): Future[TestProcess.TestResults[Json]] = ???

  override def resolve(
      idWithName: ProcessIdWithName,
      statusDetails: List[StatusDetails],
      lastStateAction: Option[ProcessAction]
  ): Future[ProcessState] =
    Future.successful(
      processStateDefinitionManager.processState(
        statusDetails.headOption.getOrElse(StatusDetails(SimpleStateStatus.NotDeployed, None))
      )
    )

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    Future.successful(WithDataFreshnessStatus.fresh(jobStatus.toList))
  }

  override def validate(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess
  ): Future[Unit] = Future.successful(())

}
