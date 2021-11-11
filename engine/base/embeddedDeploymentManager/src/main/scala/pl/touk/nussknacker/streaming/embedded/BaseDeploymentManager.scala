package pl.touk.nussknacker.streaming.embedded

import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.{CustomAction, CustomActionError, CustomActionRequest, CustomActionResult, DeploymentManager, ProcessDeploymentData, ProcessStateDefinitionManager, SavepointResult, User}
import pl.touk.nussknacker.engine.api.process.ProcessName

import scala.concurrent.Future

trait BaseDeploymentManager extends DeploymentManager {

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def customActions: List[CustomAction] = Nil

  override def invokeCustomAction(actionRequest: CustomActionRequest, processDeploymentData: ProcessDeploymentData): Future[Either[CustomActionError, CustomActionResult]] = Future.failed(new IllegalArgumentException("Not supported"))

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] = Future.failed(new IllegalArgumentException("Not supported"))

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] = Future.failed(new IllegalArgumentException("Not supported"))


}
