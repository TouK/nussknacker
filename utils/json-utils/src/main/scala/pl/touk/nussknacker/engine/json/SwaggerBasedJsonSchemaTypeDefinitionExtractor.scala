package pl.touk.nussknacker.engine.json

import io.swagger.v3.oas.models.media
import org.everit.json.schema._
import pl.touk.nussknacker.engine.json.swagger.{OpenAPISchemaParser, SwaggerTyped}

object SwaggerBasedJsonSchemaTypeDefinitionExtractor {

  def swaggerType(schema: Schema): SwaggerTyped = {
    val deserializedSchema: media.Schema[_] = OpenAPISchemaParser.parseSchema(schema.toString)
    swaggerType(deserializedSchema)
  }

  def swaggerType(schema: media.Schema[_]): SwaggerTyped = {
    SwaggerTyped(schema, Map())
  }

}
