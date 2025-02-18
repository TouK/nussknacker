package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction

import scala.concurrent.Future

object InvalidDeploymentManagerStub extends DeploymentManager {

  private val stubbedActionResponse =
    Future.failed(new ProcessIllegalAction("Can't perform action because of an error in deployment configuration"))

  private val stubbedStatus = DeploymentStatusDetails(
    ProblemStateStatus("Error in deployment configuration", allowedActions = Set.empty),
    deploymentId = None,
    version = None
  )

  override def getScenarioDeploymentsStatuses(scenarioName: ProcessName)(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[DeploymentStatusDetails]]] = {
    Future.successful(WithDataFreshnessStatus.fresh(List(stubbedStatus)))
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] = command match {
    case _: DMValidateScenarioCommand => Future.unit
    case _                            => stubbedActionResponse

  }

  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

  override def deploymentsStatusesQueryForAllScenariosSupport: DeploymentsStatusesQueryForAllScenariosSupport =
    NoDeploymentsStatusesQueryForAllScenariosSupport

  override def schedulingSupport: SchedulingSupport = NoSchedulingSupport

  override def close(): Unit = ()
}
