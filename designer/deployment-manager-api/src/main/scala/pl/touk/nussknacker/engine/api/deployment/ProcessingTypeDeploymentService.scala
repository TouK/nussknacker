package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData

import scala.concurrent.{ExecutionContext, Future}

trait ProcessingTypeDeploymentService {

  def getDeployedScenarios(implicit ec: ExecutionContext): Future[List[DeployedScenarioData]]

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
