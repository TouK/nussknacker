package pl.touk.process.model.api

import scala.concurrent.Future

trait Service {
  def invoke(params: Map[String, Any], ctx: Ctx): Future[Any]
}
