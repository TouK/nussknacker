package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentId
import pl.touk.nussknacker.engine.testing.StubbingCommands

import scala.concurrent.Future

class DeploymentManagerStub
    extends BaseDeploymentManager
    with StateQueryForAllScenariosSupported
    with StubbingCommands {

  var jobStatus: Map[ProcessName, List[StatusDetails]] = Map.empty

  def setEmptyStateStatus(): Unit = {
    jobStatus = Map.empty
  }

  def addStateStatus(
      processName: ProcessName,
      status: StateStatus,
      deploymentIdOpt: Option[PeriodicProcessDeploymentId]
  ): Unit = {
    jobStatus = jobStatus ++ Map(
      processName -> List(
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
    )
  }

  def setStateStatus(
      processName: ProcessName,
      status: StateStatus,
      deploymentIdOpt: Option[PeriodicProcessDeploymentId]
  ): Unit = {
    jobStatus = Map(
      processName -> List(
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
    )
  }

  override def resolve(
      idWithName: ProcessIdWithName,
      statusDetails: List[StatusDetails],
      lastStateAction: Option[ProcessAction],
      latestVersionId: VersionId,
      deployedVersionId: Option[VersionId],
      currentlyPresentedVersionId: Option[VersionId],
  ): Future[ProcessState] =
    Future.successful(
      processStateDefinitionManager.processState(
        statusDetails.headOption.getOrElse(StatusDetails(SimpleStateStatus.NotDeployed, None)),
        latestVersionId,
        deployedVersionId,
        currentlyPresentedVersionId,
      )
    )

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    Future.successful(WithDataFreshnessStatus.fresh(jobStatus.get(name).toList.flatten))
  }

  override def getProcessesStates()(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[Map[ProcessName, List[StatusDetails]]]] = {
    Future.successful(WithDataFreshnessStatus.fresh(jobStatus))
  }

  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

}
