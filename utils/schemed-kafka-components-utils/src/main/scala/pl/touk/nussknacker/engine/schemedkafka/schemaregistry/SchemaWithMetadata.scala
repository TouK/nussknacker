package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import io.confluent.kafka.schemaregistry.ParsedSchema

/**
 * This class holds information that are stored next to schema in registry.
 * It is lightened version of Confluent's SchemaMetadata. We don't want to use their class, because our SchemaRegistryClient
 * is not coupled with concrete schema registry implementation.
 */
case class SchemaWithMetadata(schema: ParsedSchema, id: SchemaId)