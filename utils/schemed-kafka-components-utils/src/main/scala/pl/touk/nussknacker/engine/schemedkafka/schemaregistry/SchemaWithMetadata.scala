package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaMetadata
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{AvroSchemaWithJsonPayload, OpenAPIJsonSchema}
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig

/**
 * This class holds information that are stored next to schema in registry.
 * It is lightened version of Confluent's SchemaMetadata. We don't want to use their class, because our SchemaRegistryClient
 * is not coupled with concrete schema registry implementation.
 */
case class SchemaWithMetadata private(schema: ParsedSchema, id: Int)

object SchemaWithMetadata {
  val unknownVersion: Int = -1

  def apply(schemaMetadata: SchemaMetadata, config: SchemaRegistryClientKafkaConfig): SchemaWithMetadata = {

    def withExtraSchemaTypes(schemaWithMetadata: SchemaWithMetadata) = {
      (schemaWithMetadata.schema, config.avroAsJsonSerialization) match {
        case (schema: AvroSchema, Some(true)) => SchemaWithMetadata(AvroSchemaWithJsonPayload(schema), schemaWithMetadata.id)
        case _ => schemaWithMetadata
      }
    }

    withExtraSchemaTypes(schemaMetadata.getSchemaType match {
      case "AVRO" => SchemaWithMetadata(new AvroSchema(schemaMetadata.getSchema), schemaMetadata.getId)
      case "JSON" => SchemaWithMetadata(OpenAPIJsonSchema(schemaMetadata.getSchema), schemaMetadata.getId)
      case other => throw new IllegalArgumentException(s"Not supported schema type: $other")
    })
  }

}