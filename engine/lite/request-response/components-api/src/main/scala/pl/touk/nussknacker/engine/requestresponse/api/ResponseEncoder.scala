package pl.touk.nussknacker.engine.requestresponse.api

import io.circe.Json
import org.everit.json.schema.Schema

trait ResponseEncoder[-Input] {

  def toJsonResponse(input: Input, result: List[Any]): Json

}
