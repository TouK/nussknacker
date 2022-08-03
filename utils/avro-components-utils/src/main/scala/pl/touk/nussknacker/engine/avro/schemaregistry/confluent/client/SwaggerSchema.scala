package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.swagger.v3.oas.models.media
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.json.SwaggerBasedJsonSchemaTypeDefinitionExtractor

import java.util

object SwaggerSchema {
  val TYPE = "JSON"
}

case class SwaggerSchema(swaggerSchema: media.Schema[_], jsonSchema: JsonSchema) extends ParsedSchema {

  def this(schemaString: String) = this(SwaggerBasedJsonSchemaTypeDefinitionExtractor.parseSchema(schemaString), new JsonSchema(schemaString))

  override def schemaType(): String = SwaggerSchema.TYPE

  override def name(): String = jsonSchema.name()

  override def canonicalString(): String = jsonSchema.canonicalString()

  override def references(): util.List[SchemaReference] = jsonSchema.references()

  override def isBackwardCompatible(previousSchema: ParsedSchema): util.List[String] = jsonSchema.isBackwardCompatible(previousSchema)

  override def rawSchema(): Schema = jsonSchema.rawSchema()
}
