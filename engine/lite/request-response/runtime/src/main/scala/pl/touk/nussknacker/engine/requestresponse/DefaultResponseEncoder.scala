package pl.touk.nussknacker.engine.requestresponse

import io.circe.Json
import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoder
import pl.touk.nussknacker.engine.requestresponse.api.ResponseEncoder

object DefaultResponseEncoder extends ResponseEncoder[Any] {

  override def toJsonResponse(input: Any, result: List[Any]): Json = ToJsonEncoder.default.encodeUnsafe(result)

}
