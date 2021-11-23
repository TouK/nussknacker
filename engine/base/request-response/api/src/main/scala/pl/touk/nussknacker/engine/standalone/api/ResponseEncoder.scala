package pl.touk.nussknacker.engine.standalone.api

import io.circe.Json

trait ResponseEncoder[-Input] {

  def toJsonResponse(input: Input, result: List[Any]): Json

}
