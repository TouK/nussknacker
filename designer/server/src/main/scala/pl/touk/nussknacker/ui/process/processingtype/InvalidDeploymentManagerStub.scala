package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.engine.api.deployment.ScenarioActivityHandling.AllScenarioActivitiesStoredByNussknacker
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.CustomActionDefinition
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

  override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] = command match {
    case _: DMValidateScenarioCommand => Future.unit
    case _                            => stubbedActionResponse

  }

  override def customActionsDefinitions: List[CustomActionDefinition] = List.empty

  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

  override def scenarioActivityHandling: ScenarioActivityHandling =
    AllScenarioActivitiesStoredByNussknacker

  override def close(): Unit = ()
}
