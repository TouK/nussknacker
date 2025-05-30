package pl.touk.nussknacker.ui.process.processingtype

import cats.effect.{Resource, SyncIO}
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus.GeneralProblemStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction

import scala.concurrent.Future

object InvalidDeploymentManagerStub extends DeploymentManager {

  private val stubbedActionResponse =
    Future.failed(new ProcessIllegalAction("Can't perform action because of an error in deployment configuration"))

  private val stubbedStatus = DeploymentStatusDetails(
    GeneralProblemStateStatus("Error in deployment configuration", allowedActions = Set.empty),
    deploymentId = None,
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

  override def liveDataPreviewSupport: LiveDataPreviewSupport = NoLiveDataPreviewSupport

  override def scenarioCompilationDependenciesResource: Resource[SyncIO, EngineScenarioCompilationDependencies] =
    Resource.pure(EngineScenarioCompilationDependencies.empty)

  override def close(): Unit = ()
}
