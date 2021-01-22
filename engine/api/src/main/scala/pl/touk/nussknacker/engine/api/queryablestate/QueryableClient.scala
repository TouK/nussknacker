package pl.touk.nussknacker.engine.api.queryablestate

import io.circe.Json
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.deployment.DeploymentId

import scala.concurrent.{ExecutionContext, Future}

trait QueryableClient extends AutoCloseable {

  def fetchJsonState(taskId: DeploymentId, queryName: String, key: String)(implicit ec: ExecutionContext): Future[String]

  def fetchJsonState(taskId: DeploymentId, queryName: String)(implicit ec: ExecutionContext): Future[String]

  def fetchState(taskId: DeploymentId,
                 nodeId: NodeId,
                 queryName: String,
                 transformationObject: Any,
                 keyId: String
                )(implicit ec: ExecutionContext): Future[AnyRef] = {
    Future.successful(null)
  }

}
