package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{
  DataFreshnessPolicy,
  DeployedScenarioData,
  ProcessAction,
  ProcessActionId,
  ProcessState
}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.repository.DeploymentComment
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class StubDeploymentService(states: Map[ProcessName, ProcessState]) extends DeploymentService {

  override def getProcessState(
      processDetails: processdetails.BaseProcessDetails[_]
  )(implicit user: LoggedUser, ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState] =
    getProcessState(processDetails.idWithName)

  override def getProcessState(
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser, ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState] =
    Future.successful(states(processIdWithName.name))

  override def deployProcessAsync(
      id: ProcessIdWithName,
      savepointPath: Option[String],
      deploymentComment: Option[DeploymentComment]
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Future[Option[ExternalDeploymentId]]] =
    Future.successful(Future.successful(None))

  override def cancelProcess(id: ProcessIdWithName, deploymentComment: Option[DeploymentComment])(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): Future[Unit] = Future.successful(())

  override def getDeployedScenarios(processingType: ProcessingType)(
      implicit ec: ExecutionContext
  ): Future[List[DeployedScenarioData]] =
    Future.successful(List.empty)

  override def invalidateInProgressActions(): Unit = {}

  override def markProcessFinishedIfLastActionDeploy(processingType: ProcessingType, processName: ProcessName)(
      implicit ec: ExecutionContext
  ): Future[Option[ProcessAction]] =
    Future.successful(None)

  override def markActionExecutionFinished(processingType: ProcessingType, actionId: ProcessActionId)(
      implicit ec: ExecutionContext
  ): Future[Boolean] =
    Future.successful(false)

  override def getLastStateAction(processingType: ProcessingType, processId: ProcessId)(
      implicit ec: ExecutionContext
  ): Future[Option[ProcessAction]] =
    Future.successful(None)

  override def getInProgressActionTypesForAllProcesses: Future[Map[ProcessId, Set[ProcessActionType]]] =
    Future.successful(Map.empty)

  override def fetchProcessStatesForProcesses(processes: List[processdetails.BaseProcessDetails[Unit]])(
      implicit user: LoggedUser,
      ec: ExecutionContext,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[Map[ProcessingType, ProcessState]] =
    Future.successful(Map.empty)

  override def enrichDetailsWithProcessState(
      processList: List[processdetails.BaseProcessDetails[_]]
  )(
      implicit user: LoggedUser,
      ec: ExecutionContext,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[List[processdetails.BaseProcessDetails[_]]] = Future.successful(processList)

}
