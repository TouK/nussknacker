package pl.touk.nussknacker.engine.util.typing

import io.circe.{Json, JsonObject}
import pl.touk.nussknacker.engine.api.typed.TypedMap

import scala.collection.JavaConverters._

object JsonToTypedMapConverter {

  def jsonToTypedMap(json: Json): TypedMap =
    jsonObjectToTypedMap(json.asObject.getOrElse(JsonObject.empty))

  private def jsonToAny(json: Json): Any = {
    json.fold(
      jsonNull = null,
      jsonBoolean = identity,
      jsonNumber = d => {
        //circe 0.14.1 still looses precision as was in https://github.com/circe/circe/issues/1101.
        //d.toBigDecimal.map(_.bigDecimal).getOrElse(d.toDouble)
        new java.math.BigDecimal(d.toString)
      },
      jsonString = identity,
      jsonArray = _.map(f => jsonToAny(f)).asJava,
      jsonObject = jsonObjectToTypedMap
    )
  }

  private def jsonObjectToTypedMap(jsonObject: JsonObject): TypedMap = {
    val res = jsonObject.toMap.map {
      case (jsonFieldName, json) => jsonFieldName -> jsonToAny(json)
    }
    TypedMap(res)
  }
}

