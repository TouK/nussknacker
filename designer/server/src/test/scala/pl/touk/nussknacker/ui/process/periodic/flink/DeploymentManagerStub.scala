package pl.touk.nussknacker.ui.process.periodic.flink

import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.engine.testing.StubbingCommands
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeploymentId

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

class DeploymentManagerStub extends BaseDeploymentManager with StubbingCommands {

  val jobStatus: TrieMap[ProcessName, List[StatusDetails]] = TrieMap.empty

  def getJobStatus(processName: ProcessName): Option[List[StatusDetails]] = {
    jobStatus.get(processName)
  }

  def setEmptyStateStatus(): Unit = {
    jobStatus.clear()
  }

  def addStateStatus(
      processName: ProcessName,
      status: StateStatus,
      deploymentIdOpt: Option[PeriodicProcessDeploymentId]
  ): Unit = {
    jobStatus.put(
      processName,
      List(
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
    jobStatus.put(
      processName,
      List(
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
    Future.successful(WithDataFreshnessStatus.fresh(getJobStatus(name).toList.flatten))
  }

  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

  override def schedulingSupport: SchedulingSupport = NoSchedulingSupport

  override def stateQueryForAllScenariosSupport: StateQueryForAllScenariosSupport =
    new StateQueryForAllScenariosSupported {

      override def getAllProcessesStates()(
          implicit freshnessPolicy: DataFreshnessPolicy
      ): Future[WithDataFreshnessStatus[Map[ProcessName, List[StatusDetails]]]] =
        Future.successful(WithDataFreshnessStatus.fresh(jobStatus.toMap))

    }

}
