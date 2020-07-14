package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, SchemaDeterminerError}

class BasedOnVersionAvroSchemaDeterminer(createSchemaRegistryClient: () => SchemaRegistryClient,
                                         topic: String,
                                         version: Option[Int]) extends AvroSchemaDeterminer {

  @transient private lazy val schemaRegistryClient: SchemaRegistryClient = createSchemaRegistryClient()

  override def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, Schema] =
    schemaRegistryClient
      .getFreshSchema(topic, version, isKey = false)
      .leftMap(err => new SchemaDeterminerError(s"Fetching schema error for topic: $topic, version: $version", err))

}