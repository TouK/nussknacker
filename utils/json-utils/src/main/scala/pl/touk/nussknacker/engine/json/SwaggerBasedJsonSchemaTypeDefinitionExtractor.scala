package pl.touk.nussknacker.engine.json

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import io.swagger.v3.oas.models.media
import io.swagger.v3.parser.ObjectMapperFactory
import org.everit.json.schema._
import pl.touk.nussknacker.engine.json.swagger.{OpenAPISchemaParser, SwaggerTyped}

import scala.collection.JavaConverters._

object SwaggerBasedJsonSchemaTypeDefinitionExtractor {

  private val mapper: ObjectMapper = ObjectMapperFactory.createJson()

  def swaggerType(schema: Schema): SwaggerTyped = {
    val deserializedSchema: media.Schema[_] = OpenAPISchemaParser.parseSchema(schema.toString)
    swaggerType(deserializedSchema)
  }

  def swaggerType(schema: media.Schema[_]): SwaggerTyped = {
    val refSchemas = Option(schema.getExtensions).map(_.asScala.collect {
      case (extKey, extNode: java.util.Map[String@unchecked, _]) =>
        extNode.asScala.flatMap {
          case (key: String, node: java.util.Map[String@unchecked, _]) =>
            val nodeSchema: media.Schema[_] = OpenAPISchemaParser.parseSchema(mapper.valueToTree[JsonNode](node))
            if (extKey == "$defs") {
              Map[String, media.Schema[_]](
                s"#/$extKey/$key" -> nodeSchema,
                s"/schemas/$key" -> nodeSchema)
            } else {
              Map[String, media.Schema[_]](s"#/$extKey/$key" -> nodeSchema)
            }
          case _ => Map.empty[String, media.Schema[_]]
        }
    }.flatten.toMap).getOrElse(Map.empty)
    SwaggerTyped(schema, refSchemas)
  }

}
