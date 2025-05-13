package pl.touk.nussknacker.engine.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.swagger.v3.oas.models.media
import io.swagger.v3.parser.ObjectMapperFactory
import org.everit.json.schema._
import pl.touk.nussknacker.engine.json.swagger.{OpenAPISchemaParser, SwaggerTyped}

import java.util
import scala.jdk.CollectionConverters._

object SwaggerBasedJsonSchemaTypeDefinitionExtractor {

  private val mapper: ObjectMapper = ObjectMapperFactory.createJson()

  def swaggerType(schema: Schema, parentSchema: Option[Schema] = None): SwaggerTyped = {
    val normalizedSchema                    = mapTrueSchemaToEmptySchema(schema)
    val deserializedSchema: media.Schema[_] = OpenAPISchemaParser.parseSchema(normalizedSchema.toString)
    val refsFromParent                      = parentSchema.map(collectSchemaDefs).getOrElse(Map.empty)
    val refsFromSchema                      = collectSchemaDefs(normalizedSchema)
    SwaggerTyped(deserializedSchema, refsFromParent ++ refsFromSchema)
  }

  private def mapTrueSchemaToEmptySchema(schema: Schema): Schema = {
    schema match {
      case _: TrueSchema => EmptySchema.INSTANCE
      case _             => schema
    }
  }

  // We extract schema definitions that can be used in refs using lowlevel schema extension mechanism.
  // Extensions are all redundant elements in schema. This mechanism will work onl for limited usages,
  // some constructions described here: http://json-schema.org/understanding-json-schema/structuring.html
  // like anchors, recursive schemas, nested relative schemas won't work.
  private def collectSchemaDefs(everitSchema: Schema) = {
    val schema = OpenAPISchemaParser.parseSchema(everitSchema.toString)
    Option(schema.getExtensions)
      .map(
        _.asScala
          .collect { case (extKey, extNode: util.Map[String @unchecked, _]) =>
            extNode.asScala.flatMap {
              case (key: String, node: util.Map[String @unchecked, _]) =>
                val nodeSchema: media.Schema[_] = OpenAPISchemaParser.parseSchema(mapper.valueToTree[ObjectNode](node))
                if (extKey == "$defs") {
                  Map[String, media.Schema[_]](s"#/$extKey/$key" -> nodeSchema, s"/schemas/$key" -> nodeSchema)
                } else {
                  Map[String, media.Schema[_]](s"#/$extKey/$key" -> nodeSchema)
                }
              case _ => Map.empty[String, media.Schema[_]]
            }
          }
          .flatten
          .toMap
      )
      .getOrElse(Map.empty)
  }

}
