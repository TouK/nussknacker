package pl.touk.nussknacker.ui.api.helpers

import cats.Traverse
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.repository.{DeploymentComment, ScenarioWithDetailsEntity}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import scala.language.higherKinds

import scala.concurrent.{ExecutionContext, Future}

class StubDeploymentService(states: Map[ProcessName, ProcessState]) extends DeploymentService {

  override def getProcessState(
      processDetails: ScenarioWithDetailsEntity[_]
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

  override def enrichDetailsWithProcessState[F[_]: Traverse](processTraverse: F[ScenarioWithDetails])(
      implicit user: LoggedUser,
      ec: ExecutionContext,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[F[ScenarioWithDetails]] = Future.successful(processTraverse)

}
