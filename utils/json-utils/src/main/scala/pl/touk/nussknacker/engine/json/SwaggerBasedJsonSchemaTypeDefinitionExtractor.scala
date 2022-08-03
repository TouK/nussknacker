package pl.touk.nussknacker.engine.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.swagger.v3.parser.ObjectMapperFactory
import io.swagger.v3.parser.util.OpenAPIDeserializer
import org.everit.json.schema._
import pl.touk.nussknacker.engine.json.swagger.SwaggerTyped

object SwaggerBasedJsonSchemaTypeDefinitionExtractor {

  private val mapper: ObjectMapper = ObjectMapperFactory.createJson()
  private val deserializer: OpenAPIDeserializer = new OpenAPIDeserializer()

  def swaggerType(schema: Schema): SwaggerTyped = {
    val jsonNode = mapper.readTree(schema.toString)
    val deserializedSchema = deserializer.getSchema(jsonNode.asInstanceOf[ObjectNode], null, new OpenAPIDeserializer.ParseResult())
    SwaggerTyped(deserializedSchema, Map())
  }
}
