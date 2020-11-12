package pl.touk.nussknacker.engine.avro.schemaregistry

import org.apache.avro.Schema

case class SchemaWithMetadata(schema: Schema, id: Int)
