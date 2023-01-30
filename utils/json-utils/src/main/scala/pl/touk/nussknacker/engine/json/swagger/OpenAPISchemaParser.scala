package pl.touk.nussknacker.engine.json.swagger

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.databind.node.{BaseJsonNode, ObjectNode}
import io.swagger.v3.oas.models.media
import io.swagger.v3.parser.ObjectMapperFactory
import io.swagger.v3.parser.util.OpenAPIDeserializer
import org.everit.json.schema._

object OpenAPISchemaParser {

  private val mapper: ObjectMapper = ObjectMapperFactory.createJson()

  def parseSchema(schema: String): media.Schema[_] = {
    mapper.readTree(schema) match {
      case objectNode: ObjectNode => parseSchema(objectNode)
      case node => throw new IllegalArgumentException(s"Schema must be in object representation at this point, but was: ${node.getNodeType}")
    }
  }

  def parseSchema(jsonNode: ObjectNode): media.Schema[_] = {
    val deserializer: OpenAPIDeserializer = new OpenAPIDeserializer()
    val parseResult = new OpenAPIDeserializer.ParseResult()
    parseResult.setOpenapi31(true)
    // it looks like location is not important on this level
    deserializer.getSchema(jsonNode, "", parseResult)
  }
}
