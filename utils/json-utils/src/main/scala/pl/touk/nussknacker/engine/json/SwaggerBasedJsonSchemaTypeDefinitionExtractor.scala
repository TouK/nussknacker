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
    val refSchemas = schema.getExtensions.asScala.flatMap {
      case (extKey, extNode) =>
        extNode.asInstanceOf[java.util.Map[String, AnyRef]].asScala.map {
          case (key, node) =>
            s"#/$extKey/$key" -> OpenAPISchemaParser.parseSchema(mapper.valueToTree(node), s"#/$extKey")
        }.toMap[String, media.Schema[_]]
    }.toMap[String, media.Schema[_]]
    SwaggerTyped(schema, refSchemas)
  }

}
