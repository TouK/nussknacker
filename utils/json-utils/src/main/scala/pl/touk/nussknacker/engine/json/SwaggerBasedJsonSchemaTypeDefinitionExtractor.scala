package pl.touk.nussknacker.engine.json

import io.swagger.v3.parser.OpenAPIV3Parser
import org.everit.json.schema._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.json.swagger.SwaggerTyped
import pl.touk.nussknacker.engine.json.swagger.parser.ParseSwaggerRefSchemas

import java.time.{LocalDate, LocalDateTime, LocalTime}
import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions.`iterable AsScalaIterable`

class SwaggerBasedJsonSchemaTypeDefinitionExtractor {

  def schemaWrapped(schema: Schema) = {
    val schemaRef = "\"$ref\": \"#/components/schemas/FakeSchema\""
    s"""{
       |  "paths": {
       |    "/fakepath": {
       |      "get": {
       |        "responses": {
       |          "200": {
       |            "content": {
       |              "application/json": {
       |                "schema": { $schemaRef
       |                }
       |              }
       |            }
       |          }
       |        },
       |        "operationId": "getFakeSchema"
       |      }
       |    }
       |  },
       |  "openapi": "3.0.2",
       |  "components": {
       |    "schemas": {
       |      "FakeSchema": ${schema.toString}
       |      }
       |    },
       |    "responses": {}
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
    val endpoint = openapi.getPaths.asScala.toList.head._2
    val operation = endpoint.readOperationsMap().asScala.toList.head._2
    val responseDefinition = operation.getResponses.get("200")
    val openApiSchema = responseDefinition.getContent.get("application/json").getSchema
    SwaggerTyped(openApiSchema, ParseSwaggerRefSchemas(openapi))
  }

}

object SwaggerBasedJsonSchemaTypeDefinitionExtractor {

  private lazy val fieldsExtractor = new SwaggerBasedJsonSchemaTypeDefinitionExtractor


  def typeDefinition(schema: Schema): TypingResult =
    fieldsExtractor.swaggerTypeDefinition(schema)

  def swaggerType(schema: Schema): SwaggerTyped =
    fieldsExtractor.swaggerType(schema)

}
