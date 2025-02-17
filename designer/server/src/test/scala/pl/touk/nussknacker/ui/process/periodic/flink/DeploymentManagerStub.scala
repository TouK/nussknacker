package pl.touk.nussknacker.ui.process.periodic.flink

import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeploymentId

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

class DeploymentManagerStub extends BaseDeploymentManager {

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
          errors = Nil
        )
      )
    )
  }

  override def getScenarioDeploymentsStatuses(
      scenarioName: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    Future.successful(WithDataFreshnessStatus.fresh(getJobStatus(scenarioName).toList.flatten))
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

  override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] = command match {
    case _: DMValidateScenarioCommand => Future.successful(())
    case _: DMRunDeploymentCommand    => Future.successful(None)
    case _: DMCancelScenarioCommand   => Future.successful(())
    case _: DMCancelDeploymentCommand => Future.successful(())
    case _: DMStopScenarioCommand | _: DMStopDeploymentCommand | _: DMMakeScenarioSavepointCommand |
        _: DMRunOffScheduleCommand | _: DMTestScenarioCommand =>
      notImplemented
  }

}
