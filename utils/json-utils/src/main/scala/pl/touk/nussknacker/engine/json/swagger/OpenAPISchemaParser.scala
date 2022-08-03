package pl.touk.nussknacker.engine.json.swagger

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.swagger.v3.oas.models.media
import io.swagger.v3.parser.ObjectMapperFactory
import io.swagger.v3.parser.util.OpenAPIDeserializer
import org.everit.json.schema._

object OpenAPISchemaParser {

  private val mapper: ObjectMapper = ObjectMapperFactory.createJson()

  def parseSchema(schema: String): media.Schema[_] = {
    val deserializer: OpenAPIDeserializer = new OpenAPIDeserializer()
    val jsonNode = mapper.readTree(schema)
    deserializer.getSchema(jsonNode.asInstanceOf[ObjectNode], null, new OpenAPIDeserializer.ParseResult())
  }
}
