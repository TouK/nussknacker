package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId}

import scala.concurrent.{ExecutionContext, Future}

trait ProcessingTypeDeploymentService {

  def getDeployedScenarios(implicit ec: ExecutionContext): Future[List[DeployedScenarioData]]

  // TODO: This method is for backward compatibility. Remove it after switching all Flink jobs into mandatory deploymentId in StatusDetails
  def markProcessFinishedIfLastActionDeploy(processName: ProcessName)(
      implicit ec: ExecutionContext
  ): Future[Option[ProcessAction]]

  // Marks action execution finished. Returns true if update has some effect
  def markActionExecutionFinished(actionId: ProcessActionId)(implicit ec: ExecutionContext): Future[Boolean]

  def getLastStateAction(processId: ProcessId)(implicit ec: ExecutionContext): Future[Option[ProcessAction]]

}

final case class DeployedScenarioData(
    processVersion: ProcessVersion,
    deploymentData: DeploymentData,
    resolvedScenario: CanonicalProcess
)

class ProcessingTypeDeploymentServiceStub(
    deployedScenarios: List[DeployedScenarioData]
) extends ProcessingTypeDeploymentService {

  @volatile
  var actionIds: List[ProcessActionId] = List.empty

  override def getDeployedScenarios(implicit ec: ExecutionContext): Future[List[DeployedScenarioData]] =
    Future.successful(deployedScenarios)

  override def markProcessFinishedIfLastActionDeploy(processName: ProcessName)(
      implicit ec: ExecutionContext
  ): Future[Option[ProcessAction]] =
    Future.successful(None)

  override def markActionExecutionFinished(
      actionId: ProcessActionId
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    actionIds = actionId :: actionIds
    Future.successful(true)
  }

  override def getLastStateAction(processId: ProcessId)(implicit ec: ExecutionContext): Future[Option[ProcessAction]] =
    Future.successful(None)

  def sentActionIds: List[ProcessActionId] = actionIds

}
