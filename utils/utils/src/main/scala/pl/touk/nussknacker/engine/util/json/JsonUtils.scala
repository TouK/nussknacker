package pl.touk.nussknacker.engine.util.json

import io.circe.Json
import pl.touk.nussknacker.engine.util.Implicits._
import scala.jdk.CollectionConverters._

object JsonUtils {
  def jsonToAny(json: Json): Any = json.fold(
    jsonNull = null,
    jsonBoolean = identity[Boolean],
    jsonNumber = _.toBigDecimal.map(_.bigDecimal).orNull, //we need here java BigDecimal type
    jsonString = identity[String],
    jsonArray = _.map(jsonToAny).asJava,
    jsonObject = _.toMap.mapValuesNow(jsonToAny).asJava
  )
}
