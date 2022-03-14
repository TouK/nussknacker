package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.source.response

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.util.JsonEncodings

object ResponseAggregationStrategy extends LazyLogging {

  def array(processId: String)(results: List[Any]): Json = {
    Json.fromValues(toJsons(results))
  }

  def head(processId: String)(results: List[Any]): Json = {
    val jsonResults = toJsons(results)
    if(jsonResults.size > 1) logger.warn(s"Process $processId returned more than one result when exactly one expected (responseAggregation = 'Single sink'), results: $jsonResults")
    jsonResults
      .headOption
      .getOrElse(throw new IllegalArgumentException(s"Process $processId did not return any result, if you think it's a bug, please contact with the process owner"))
  }

  private def toJsons(results: List[Any]): List[Json] = {
    results
      .filterNot(isUnit)
      .filterNot(_ == null)
      .map(JsonEncodings.encodeJson)
  }

  private def isUnit[T](a: T): Boolean = a == ((): Unit)
}
