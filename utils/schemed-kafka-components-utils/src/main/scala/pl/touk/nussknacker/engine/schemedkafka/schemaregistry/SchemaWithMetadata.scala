package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils

/**
 * This class holds information that are stored next to schema in registry.
 * It is lightened version of Confluent's SchemaMetadata. We don't want to use their class, because our SchemaRegistryClient
 * is not coupled with concrete schema registry implementation.
 * This class ensures that ParsedSchema will be in the form expected by other mechanisms (see AvroUtils.adjustParsedSchema)
 */
case class SchemaWithMetadata private(schema: ParsedSchema, id: SchemaId)

object SchemaWithMetadata {

  def apply(schema: ParsedSchema, id: SchemaId): SchemaWithMetadata = {
    new SchemaWithMetadata(AvroUtils.adjustParsedSchema(schema), id)
  }

  def fromRawSchema(schemaType: String, schemaContent: String, id: SchemaId): SchemaWithMetadata = {
    new SchemaWithMetadata(AvroUtils.toParsedSchema(schemaType, schemaContent), id)
  }

}