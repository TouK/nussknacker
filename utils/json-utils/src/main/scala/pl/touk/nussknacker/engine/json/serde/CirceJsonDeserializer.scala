package pl.touk.nussknacker.engine.json.serde

import org.everit.json.schema.Schema
import org.json.JSONTokener
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.json.SwaggerBasedJsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.json.swagger.SwaggerTyped
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonToNuStruct
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
    //we do parsing for:
    //1. for schema validation
    //2. for typing purposes which is based on Circe
    val inputJson = new JSONTokener(string).nextValue()

    val validatedJson = jsonSchema
      .validateData(inputJson)
      .valueOr(errorMsg => throw CustomNodeValidationException(errorMsg, None))

    val circeJson = JsonSchemaUtils.jsonToCirce(validatedJson)
    val struct = JsonToNuStruct(circeJson, swaggerTyped)
    struct
  }

}
