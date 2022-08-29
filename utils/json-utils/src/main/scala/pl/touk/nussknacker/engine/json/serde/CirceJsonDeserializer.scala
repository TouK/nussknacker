package pl.touk.nussknacker.engine.json.serde

import cats.data.Validated
import io.circe
import io.circe.Json
import org.everit.json.schema.{Schema, ValidationException}
import org.json.{JSONException, JSONTokener}
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.json.SwaggerBasedJsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.json.swagger.SwaggerTyped
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonToObject

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.asScalaBufferConverter

class CirceJsonDeserializer(jsonSchema: Schema) {

  val swaggerTyped: SwaggerTyped = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(jsonSchema)

  def deserialize(bytes: Array[Byte]): Validated[circe.Error, Any] = {
    val string = new String(bytes, StandardCharsets.UTF_8)
    deserialize(string)
  }

  def deserialize(string: String): Validated[circe.Error, Any] = {
    //we basically do parsing twice:
    //1. for schema validation
    //2. for typing purposes which is based on Circe
    val jsonObject = new JSONTokener(string).nextValue()
    //after validate jsonObject has set default field values
    catchValidationError(jsonSchema.validate(jsonObject))
    Validated.fromEither(CirceUtil.decodeJson[Json](jsonObject.toString).map(JsonToObject.apply(_, swaggerTyped)))
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
