package pl.touk.nussknacker.engine.avro.schemaregistry

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaMetadata
import io.confluent.kafka.schemaregistry.json.JsonSchema

/**
 * This class holds information that are stored next to schema in registry.
 * It is lightened version of Confluent's SchemaMetadata. We don't want to use their class, because our SchemaRegistryClient
 * is not coupled with concrete schema registry implementation.
 */
case class SchemaWithMetadata(schema: ParsedSchema, id: Int)

object SchemaWithMetadata {
  def apply(schema: SchemaMetadata): SchemaWithMetadata = {
    val parsedSchema = schema.getSchemaType match {
      case "avro" => new AvroSchema(schema.getSchema)
      case "json" => new JsonSchema(schema.getSchema)
      case other => throw new IllegalArgumentException(s"Not supported schema type: $other")
    }
    SchemaWithMetadata(parsedSchema, schema.getId)
  }
}