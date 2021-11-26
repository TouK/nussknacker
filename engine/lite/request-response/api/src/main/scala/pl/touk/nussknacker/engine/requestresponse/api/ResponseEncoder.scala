package pl.touk.nussknacker.engine.requestresponse.api

import io.circe.Json

trait ResponseEncoder[-Input] {

  def toJsonResponse(input: Input, result: List[Any]): Json

}
