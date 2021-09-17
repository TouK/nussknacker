package pl.touk.nussknacker.engine.util.typing

import io.circe.{Json, JsonObject}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import scala.collection.JavaConverters._

object JsonToTypedMapConverter {

  def jsonToTypedMap(json: Json): TypedMap =
    jsonObjectToTypedMap(json.asObject.getOrElse(JsonObject.empty))

  private def jsonToAny(json: Json): Any = {
    json.fold(
      jsonNull = null,
      jsonBoolean = identity,
      jsonNumber = d => d.toBigDecimal.map(_.bigDecimal).getOrElse(d.toDouble),
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

object JsonSchemaToTypingResultConverter {

  final val TYPE_FIELD = "type"
  final val DEFAULT_FIELD = "default"
  final val PROPERTIES_FIELD = "properties"
  final val ARRAY_ITEMS_FIELD = "items"

  /* *
  * It is simplified schema parser. We define schema as an schema type=object : "{ "properties": { our definition } }"
  * Rules:
  * - Any type=object to have "properties" field
  * - Any type=array to have "items" field
  * */

  def jsonSchemaToTypingResult(definition: String): TypingResult = {
    val json = CirceUtil.decodeJsonUnsafe[Json](definition, "Provided json-schema is not valid")
    parseJsonObject(json)
  }

  private def parseJsonObject(json: Json): TypingResult = {
    val properties = json.hcursor.downField(PROPERTIES_FIELD).focus
    if (properties.isEmpty) {
      throw new IllegalArgumentException(s"There should be exactly one '$PROPERTIES_FIELD' field in 'object'.")
    }
    else jsonValuesToTypingResult(properties.get)
  }

  private def jsonValuesToTypingResult(json: Json): TypingResult = {
    if (containsFieldType(json)) resolveJsonTypingResult(json)
    else {
      TypedObjectTypingResult(getJsonFirstLevelFieldsMap(json).mapValues(jsonValuesToTypingResult).toList)
    }
  }

  private def containsFieldType(json: Json): Boolean = {
    lazy val keyNames = getKeyNames(json)
    keyNames.contains(TYPE_FIELD)
  }

  def getJsonFirstLevelFieldsMap(json: Json): Map[String, Json] = {
    for {
      jobject <- json.asObject.toList
      (key, value) <- jobject.toVector
    } yield (key, value)
  }.toMap

  def getKeyNames(json: Json): List[String] = for {
    jobject <- json.asObject.toList
    k <- jobject.keys
  } yield k

  def resolveJsonTypingResult(json: Json): TypingResult = {
    val typeValue = json.hcursor.downField(TYPE_FIELD).as[String].getOrElse("null")
    typeValue match {
      case "array" => getArrayTypingResult(json)
      case "boolean" => Typed.typedClass[Boolean]
      case "integer" => Typed.typedClass[Integer]
      case "number" => Typed.typedClass[java.math.BigDecimal]
      case "null" => Typed.typedClass[Null]
      case "object" => parseJsonObject(json)
      case "string" => Typed.typedClass[String]
    }
  }

  def getArrayTypingResult(json: Json): TypingResult = {
    val arrayTypes = json.hcursor.downField(ARRAY_ITEMS_FIELD).focus
    val arrayTypingResult = if (arrayTypes.isEmpty) {
      throw new IllegalArgumentException(s"There should be exactly one '$ARRAY_ITEMS_FIELD' field in 'array'.")
    }
    else resolveJsonTypingResult(arrayTypes.get)
    Typed.genericTypeClass[java.util.List[_]](List(arrayTypingResult))
  }

}

