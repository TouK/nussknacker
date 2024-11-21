package pl.touk.nussknacker.engine.api.json

import io.circe.Json
import pl.touk.nussknacker.engine.util.Implicits._

import scala.jdk.CollectionConverters._

object FromJsonDecoder {

  def jsonToAny(json: Json): Any = json.fold(
    jsonNull = null,
    jsonBoolean = identity[Boolean],
    jsonNumber = jsonNumber =>
      // we pick the narrowest type as possible to reduce the amount of memory and computations overheads
      jsonNumber.toInt orElse
        jsonNumber.toLong orElse
        // We prefer java big decimal over float/double
        jsonNumber.toBigDecimal.map(_.bigDecimal)
        getOrElse (throw new IllegalArgumentException(s"Not supported json number: $jsonNumber")),
    jsonString = identity[String],
    jsonArray = _.map(jsonToAny).asJava,
    jsonObject = _.toMap.mapValuesNow(jsonToAny).asJava
  )

}
