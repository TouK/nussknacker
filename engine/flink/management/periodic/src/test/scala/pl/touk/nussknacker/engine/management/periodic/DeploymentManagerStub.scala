package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentId
import pl.touk.nussknacker.engine.testing.StubbingCommands

import scala.concurrent.Future

class DeploymentManagerStub extends BaseDeploymentManager with StubbingCommands {

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

  override def resolve(
      idWithName: ProcessIdWithName,
      statusDetails: List[StatusDetails],
      lastStateAction: Option[ProcessAction],
      latestVersionId: VersionId,
      deployedVersionId: Option[VersionId],
  ): Future[ProcessState] =
    Future.successful(
      processStateDefinitionManager.processState(
        statusDetails.headOption.getOrElse(StatusDetails(SimpleStateStatus.NotDeployed, None)),
        latestVersionId,
        deployedVersionId
      )
    )

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    Future.successful(WithDataFreshnessStatus.fresh(jobStatus.toList))
  }

  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

}
