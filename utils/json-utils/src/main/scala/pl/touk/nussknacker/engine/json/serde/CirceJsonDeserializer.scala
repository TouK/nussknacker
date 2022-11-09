package pl.touk.nussknacker.engine.json.serde

import io.circe.Json._
import io.circe.{Json, JsonObject}
import org.everit.json.schema.{Schema, ValidationException}
import org.json.{JSONArray, JSONException, JSONObject, JSONTokener}
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.json.SwaggerBasedJsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.json.swagger.SwaggerTyped
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonToTypedMap

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.{asScalaBufferConverter, asScalaIteratorConverter, iterableAsScalaIterableConverter}

class CirceJsonDeserializer(jsonSchema: Schema) {

  private val swaggerTyped: SwaggerTyped = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(jsonSchema)

  def deserialize(bytes: Array[Byte]): AnyRef = {
    val string = new String(bytes, StandardCharsets.UTF_8)
    deserialize(string)
  }

  def deserialize(string: String): AnyRef = {
    //we do parsing for:
    //1. for schema validation
    //2. for typing purposes which is based on Circe
    val jsonObject = new JSONTokener(string).nextValue()
    //after validate jsonObject has set default field values
    catchValidationError(jsonSchema.validate(jsonObject))
    JsonToTypedMap(toCirce(jsonObject), swaggerTyped)
  }

  private def toCirce(json: Object): Json = json match {
    case a: java.lang.Boolean => fromBoolean(a)
    case a: java.lang.Double => fromBigDecimal(BigDecimal.valueOf(a))
    case a: java.lang.Integer => fromInt(a)
    case a: java.lang.Long => fromLong(a)
    case a: java.lang.String => fromString(a)
    case a: JSONArray => fromValues(a.asScala.map(toCirce))
    case a if a == JSONObject.NULL => Null
    case a: JSONObject => fromJsonObject(JsonObject.fromIterable(a.keys().asScala.map(name => (name, toCirce(a.get(name)))).toIterable))
    //should not happen, based on JSONTokener.nextValue docs
    case a => throw new IllegalArgumentException(s"Should not happen, JSON: ${a.getClass}")
  }

  protected def prepareValidationErrorMessage(exception: Throwable): String = {
    exception match {
      case ve: ValidationException => ve.getAllMessages.asScala.mkString("\n\n")
      case je: JSONException => s"Invalid JSON: ${je.getMessage}"
      case _ => "unknown error message"
    }
  }

  protected def catchValidationError[T](action: => T): T = try {
    action
  } catch {
    case ex: Throwable =>
      val errorMsg = prepareValidationErrorMessage(ex)
      throw CustomNodeValidationException(errorMsg, None)
  }
}
