package pl.touk.nussknacker.engine.json

import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import org.everit.json.schema._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.json.swagger.SwaggerTyped
import pl.touk.nussknacker.engine.json.swagger.parser.ParseSwaggerRefSchemas

import java.time.{LocalDate, LocalDateTime, LocalTime}
import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions.`iterable AsScalaIterable`

class SwaggerBasedJsonSchemaTypeDefinitionExtractor {

  private val fakeSchema = """FakeSchema"""

  private def schemaWrapped(schema: Schema) = {
    s"""{
       |  "openapi": "3.1.0",
       |  "components": {
       |    "schemas": {
       |      "$fakeSchema": ${schema.toString}
       |    }
       |  }
       |}""".stripMargin
  }


  def swaggerTypeDefinition(schema: Schema): TypingResult = {
    swaggerType(schema).typingResult
  }

  def swaggerType(schema: Schema): SwaggerTyped = {
    val rawSwagger = schemaWrapped(schema)
    val swagger30 = new OpenAPIV3Parser().readContents(rawSwagger)
    val openapi = swagger30.getOpenAPI
    val refSchemas = ParseSwaggerRefSchemas(openapi)
    val openApiSchema = openapi.getComponents.getSchemas.get(fakeSchema)
    SwaggerTyped(openApiSchema, refSchemas)
  }
}

object SwaggerBasedJsonSchemaTypeDefinitionExtractor {

  private lazy val fieldsExtractor = new SwaggerBasedJsonSchemaTypeDefinitionExtractor


  def typeDefinition(schema: Schema): TypingResult =
    fieldsExtractor.swaggerTypeDefinition(schema)

  def swaggerType(schema: Schema): SwaggerTyped =
    fieldsExtractor.swaggerType(schema)

}
