package pl.touk.nussknacker.engine.avro.schemaregistry

import org.apache.avro.Schema

/**
 * This class holds information that are stored next to schema in registry.
 * It is lightened version of Confluent's SchemaMetadata. We don't want to use their class, because our SchemaRegistryClient
 * is not coupled with concrete schema registry implementation.
 */
case class SchemaWithMetadata(schema: SchemaContainer, id: Int)

sealed trait SchemaContainer

case class JsonSchema(schema: org.everit.json.schema.Schema) extends SchemaContainer
case class AvroSchema(schema: org.apache.avro.Schema) extends SchemaContainer
