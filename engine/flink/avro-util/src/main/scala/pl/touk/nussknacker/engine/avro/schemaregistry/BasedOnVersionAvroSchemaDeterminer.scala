package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, SchemaDeterminerError, SchemaWithId}

class BasedOnVersionAvroSchemaDeterminer(schemaRegistryClient: SchemaRegistryClient,
                                         topic: String,
                                         versionOption: SchemaVersionOption) extends AvroSchemaDeterminer {

  override def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, SchemaWithId] = {
    val version = versionOption match {
      case ExistingSchemaVersion(v) => Some(v)
      case LatestSchemaVersion => None
    }
    schemaRegistryClient
      .getFreshSchema(topic, version, isKey = false)
      .leftMap(err => new SchemaDeterminerError(s"Fetching schema error for topic: $topic, version: $versionOption", err))
      .map(withMetadata => SchemaWithId(withMetadata.schema, Some(withMetadata.id)))
  }

}
