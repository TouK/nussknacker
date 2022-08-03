package pl.touk.nussknacker.engine.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.swagger.v3.oas.models.media
import io.swagger.v3.parser.ObjectMapperFactory
import io.swagger.v3.parser.util.OpenAPIDeserializer
import org.everit.json.schema._
import pl.touk.nussknacker.engine.json.swagger.SwaggerTyped

object SwaggerBasedJsonSchemaTypeDefinitionExtractor {

  private val mapper: ObjectMapper = ObjectMapperFactory.createJson()
  private val deserializer: OpenAPIDeserializer = new OpenAPIDeserializer()

  def swaggerType(schema: Schema): SwaggerTyped = {
    val deserializedSchema: media.Schema[_] = parseSchema(schema.toString)
    swaggerType(deserializedSchema)
  }

  def swaggerType(deserializedSchema: media.Schema[_]): SwaggerTyped = {
    SwaggerTyped(deserializedSchema, Map())
  }

  def parseSchema(schema: String): media.Schema[_] = {
    val jsonNode = mapper.readTree(schema)
    deserializer.getSchema(jsonNode.asInstanceOf[ObjectNode], null, new OpenAPIDeserializer.ParseResult())
  }
}
