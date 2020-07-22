package pl.touk.nussknacker.engine.api.queryablestate

import scala.concurrent.{ExecutionContext, Future}

trait QueryableClient extends AutoCloseable {

  def fetchJsonState(taskId: String, queryName: String, key: String)(implicit ec: ExecutionContext): Future[String]

  def fetchJsonState(taskId: String, queryName: String)(implicit ec: ExecutionContext): Future[String]

}
