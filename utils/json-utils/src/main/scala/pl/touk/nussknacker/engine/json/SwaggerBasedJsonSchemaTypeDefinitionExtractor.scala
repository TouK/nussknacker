package pl.touk.nussknacker.engine.json

import com.fasterxml.jackson.databind.ObjectMapper
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
    val refSchemas = schema.getExtensions.asScala.collect {
      case (extKey, extNode: java.util.Map[String@unchecked, _]) =>
        extNode.asScala.collect {
          case (key, node: java.util.Map[String@unchecked, _]) =>
            s"#/$extKey/$key" -> OpenAPISchemaParser.parseSchema(mapper.valueToTree(node), s"#/$extKey")
        }.toMap[String, media.Schema[_]]
    }.flatten.toMap[String, media.Schema[_]]
    SwaggerTyped(schema, refSchemas)
  }

}
