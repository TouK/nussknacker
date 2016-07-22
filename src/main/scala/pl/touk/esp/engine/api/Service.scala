package pl.touk.esp.engine.api

import scala.concurrent.Future

trait Service {
  def invoke(params: Map[String, Any]): Future[Any]
}
