package pl.touk.nussknacker.engine.requestresponse

import io.circe.Json
import pl.touk.nussknacker.engine.requestresponse.api.ResponseEncoder
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder

object DefaultResponseEncoder extends ResponseEncoder[Any] {

  private val encoder = ToJsonEncoder(failOnUnknown = true, getClass.getClassLoader)

  override def toJsonResponse(input: Any, result: List[Any]): Json = encoder.encode(result)

}
