package pl.touk.nussknacker.engine.standalone.utils.typing

import io.circe.{Json, JsonObject}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import scala.collection.JavaConverters._

object TypedMapUtils {

    def jsonToTypedMap(json: Json): TypedMap =
      jsonObjectToTypedMap(json.asObject.getOrElse(JsonObject.empty))

    private def jsonToAny(json: Json): Any = {
      json.fold(
        jsonNull = null,
        jsonBoolean = identity,
        jsonNumber = d => BigDecimal(d.toDouble), //prevent scientific notation
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
