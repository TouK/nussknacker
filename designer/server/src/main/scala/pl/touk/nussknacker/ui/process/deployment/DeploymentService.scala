package pl.touk.nussknacker.ui.process.deployment

import cats.Traverse
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

trait DeploymentService extends ProcessStateService {

  // Inner Future in result allows to wait for deployment finish, while outer handles validation
  // We split deploy process that way because we want to be able to split FE logic into two phases:
  // - validations - it is quick part, the result will be displayed on deploy modal
  // - deployment on engine side - it is longer part, the result will be shown as a notification
  def deployProcessAsync(
      id: ProcessIdWithName,
      savepointPath: Option[String],
      comment: Option[String]
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Future[Option[ExternalDeploymentId]]]

  def cancelProcess(id: ProcessIdWithName, comment: Option[String])(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): Future[Unit]

  def invalidateInProgressActions(): Unit

  // Marks action execution finished. Returns true if update has some effect
  def markActionExecutionFinished(processingType: ProcessingType, actionId: ProcessActionId)(
      implicit ec: ExecutionContext
  ): Future[Boolean]

  def getLastStateAction(processingType: ProcessingType, processId: ProcessId)(
      implicit ec: ExecutionContext
  ): Future[Option[ProcessAction]]

  def invokeCustomAction(
      actionName: ScenarioActionName,
      processIdWithName: ProcessIdWithName,
      params: Map[String, String]
  )(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): Future[CustomActionResult]

}

trait ProcessStateService {

  def enrichDetailsWithProcessState[F[_]: Traverse](processTraverse: F[ScenarioWithDetails])(
      implicit user: LoggedUser,
      ec: ExecutionContext,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[F[ScenarioWithDetails]]

  def getProcessState(
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser, ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState]

  def getProcessState(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit user: LoggedUser, ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState]

}
