package pl.touk.nussknacker.test.mock

import cats.Traverse
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.deployment.{ExternalDeploymentId, User}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.repository.{DeploymentComment, ScenarioWithDetailsEntity}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

import scala.language.higherKinds

import scala.language.higherKinds

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

  override def invokeCustomAction(
      actionName: ScenarioActionName,
      processIdWithName: ProcessIdWithName,
      params: Map[String, String]
  )(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): Future[CustomActionResult] =
    Future.successful(
      CustomActionResult(
        req = CustomActionRequest(actionName, ProcessVersion.empty, User(loggedUser.id, loggedUser.username), params),
        msg = "stub response"
      )
    )

}
