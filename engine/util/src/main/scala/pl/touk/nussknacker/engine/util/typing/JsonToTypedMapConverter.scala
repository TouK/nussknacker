package pl.touk.nussknacker.engine.util.typing

import com.typesafe.scalalogging.LazyLogging
import io.circe.{Json, JsonObject}
import org.everit.json.schema.{ArraySchema, BooleanSchema, FalseSchema, NullSchema, NumberSchema, ObjectSchema, Schema, StringSchema, TrueSchema}
import pl.touk.nussknacker.engine.api.typed.TypedMap
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

object SchemaToTypingResultConverter {

  /* *
  * It is simplified schema parser. We define schema as an schema type=object : "{ "properties": { our definition } }"
  * Rules:
  * - Any type=object to have "properties" field
  * - Any type=array to have "items" field
  * */

  def jsonSchemaToTypingResult(schema: Schema): TypingResult = {
    schema match {
      case os: ObjectSchema => parseObjectSchema(os)
      case _ => throw new IllegalArgumentException("Schema should be represented as object schema \"type\": \"object\"")
    }
  }

  private def parseObjectSchema(schema: ObjectSchema): TypingResult = {
    val propertySchemas = schema.getPropertySchemas.asScala.toMap
    jsonValuesToTypingResult(propertySchemas)
  }

  private def jsonValuesToTypingResult(namedSchema: Map[String, Schema]): TypingResult = {
    TypedObjectTypingResult(namedSchema.mapValues(resolveJsonTypingResult).toList)
  }

  def resolveJsonTypingResult(schema: Schema): TypingResult = {
    schema match {
      case s:ArraySchema => getArrayTypingResult(s)
      case s:ObjectSchema => parseObjectSchema(s)
      case s:NumberSchema => if(s.requiresInteger()) Typed.typedClass[Integer] else Typed.typedClass[java.math.BigDecimal]
      case _:BooleanSchema => Typed.typedClass[Boolean]
      case _:TrueSchema => Typed.typedClass[Boolean]
      case _:FalseSchema => Typed.typedClass[Boolean]
      case _:NullSchema => Typed.typedClass[Null]
      case _:StringSchema => Typed.typedClass[String]
      case s => {
        throw new IllegalArgumentException(s"Schema '${s.toString}' is not supported yet.")
      }
    }
  }

  def getArrayTypingResult(schema: ArraySchema): TypingResult = {
    val itemSchema = schema.getAllItemSchema
    Typed.genericTypeClass[java.util.List[_]](List(resolveJsonTypingResult(itemSchema)))
  }

}

