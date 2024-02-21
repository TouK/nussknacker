package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentId, User}

import scala.concurrent.Future

trait BaseDeploymentManager extends DeploymentManager {

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] = {
    Future.failed(new UnsupportedOperationException(s"Making of savepoint is not supported"))
  }

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] = {
    Future.failed(new UnsupportedOperationException(s"Stopping of scenario is not supported"))
  }

  override def stop(
      name: ProcessName,
      deploymentId: DeploymentId,
      savepointDir: Option[String],
      user: User
  ): Future[SavepointResult] =
    Future.failed(new UnsupportedOperationException(s"Stopping of deployment is not supported"))

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def close(): Unit = {}

  override def customActions: List[CustomAction] = List.empty

  override def invokeCustomAction(
      actionRequest: ActionRequest,
      canonicalProcess: CanonicalProcess
  ): Future[ActionResult] =
    Future.failed(new NotImplementedError())

}
