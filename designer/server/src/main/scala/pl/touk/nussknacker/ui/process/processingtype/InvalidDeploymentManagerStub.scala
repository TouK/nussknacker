package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
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
      lastStateAction: Option[ProcessAction],
      latestVersionId: VersionId,
      deployedVersionId: Option[VersionId],
      currentlyPresentedVersionId: Option[VersionId],
  ): Future[ProcessState] = {
    Future.successful(
      processStateDefinitionManager.processState(
        stubbedStatus,
        latestVersionId,
        deployedVersionId,
        currentlyPresentedVersionId
      )
    )
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] = command match {
    case _: DMValidateScenarioCommand => Future.unit
    case _                            => stubbedActionResponse

  }

  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

  override def stateQueryForAllScenariosSupport: StateQueryForAllScenariosSupport = NoStateQueryForAllScenariosSupport

  override def periodicExecutionSupport: PeriodicExecutionSupport = NoPeriodicExecutionSupport

  override def close(): Unit = ()
}
