package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, SchemaDeterminerError}

class BasedOnVersionAvroSchemaDeterminer(schemaRegistryClient: SchemaRegistryClient,
                                         topic: String,
                                         versionOption: SchemaVersionOption) extends AvroSchemaDeterminer {

  override def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, Schema] = {
    val version = versionOption match {
      case ExistingSchemaVersion(v) => Some(v)
      case LatestSchemaVersion => None
    }
    schemaRegistryClient
      .getFreshSchema(topic, version, isKey = false)
      .leftMap(err => new SchemaDeterminerError(s"Fetching schema error for topic: $topic, version: $versionOption", err))
  }

}