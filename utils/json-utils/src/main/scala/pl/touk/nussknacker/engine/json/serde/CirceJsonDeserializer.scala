package pl.touk.nussknacker.engine.json.serde

import cats.data.Validated
import io.circe
import io.circe.Json
import org.everit.json.schema.Schema
import org.json.JSONTokener
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.json.SwaggerBasedJsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.json.swagger.SwaggerTyped
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonToObject

import java.nio.charset.StandardCharsets

class CirceJsonDeserializer(jsonSchema: Schema) {

  val swaggerTyped: SwaggerTyped = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(jsonSchema)

  def deserialize(bytes: Array[Byte]): Validated[circe.Error, Any] = {
    val string = new String(bytes, StandardCharsets.UTF_8)
    deserialize(string)
  }

  def deserialize(string: String): Validated[circe.Error, Any] = {
    val jsonObject = new JSONTokener(string).nextValue()
    jsonSchema.validate(jsonObject)
    Validated.fromEither(CirceUtil.decodeJson[Json](string).map(JsonToObject.apply(_, swaggerTyped)))
  }
}
