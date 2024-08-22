package pl.touk.nussknacker.engine.json.serde

import org.everit.json.schema.Schema
import org.json.JSONTokener
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.json.SwaggerBasedJsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.json.swagger.SwaggerTyped
import pl.touk.nussknacker.engine.json.swagger.extractor.FromJsonDecoder
import pl.touk.nussknacker.engine.util.json.JsonSchemaUtils

import java.nio.charset.StandardCharsets

class CirceJsonDeserializer(jsonSchema: Schema) {

  import pl.touk.nussknacker.engine.util.json.JsonSchemaImplicits._

  private val swaggerTyped: SwaggerTyped = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(jsonSchema)

  def deserialize(bytes: Array[Byte]): AnyRef = {
    val string = new String(bytes, StandardCharsets.UTF_8)
    deserialize(string)
  }

  def deserialize(string: String): AnyRef = {
    // we do parsing for:
    // 1. for schema validation
    // 2. for typing purposes which is based on Circe
    val inputJson = new JSONTokener(string).nextValue()

    // WARNING: everit mutates inputJson. Empty map become map with existing field with null value. For us in most cases it is not
    //          problematic because we treat empty map and map with null values the same, but for some edge cases it might be tricky.
    // TODO: We should consider replacing everit by networknt's json-schema-validator (it uses jackson under the hood)
    //       The additional benefits is that it generates more readable validation errors.
    val validatedJson = jsonSchema
      .validateData(inputJson)
      .valueOr(errorMsg => throw CustomNodeValidationException(errorMsg, None))

    val circeJson = JsonSchemaUtils.jsonToCirce(validatedJson)
    val struct    = FromJsonDecoder.decode(circeJson, swaggerTyped)
    struct
  }

}
