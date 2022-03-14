package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.util

import io.circe.Json
import io.circe.Json._
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

object JsonEncodings {

  private val safeBigDecimal: BigDecimal => Json = safeJson[BigDecimal](fromBigDecimal)

  def encodeJson(json: Any): Json = BestEffortJsonEncoder(failOnUnkown = false, getClass.getClassLoader).encode(json)

  private def safeJson[T](fun: T => Json): T => Json = (value: T) => Option(value) match {
    case Some(realValue) => fun(realValue)
    case None => Null
  }

}
