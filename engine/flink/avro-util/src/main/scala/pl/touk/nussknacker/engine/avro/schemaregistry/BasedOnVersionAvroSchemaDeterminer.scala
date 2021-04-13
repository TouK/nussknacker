package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import cats.data.Validated.Valid
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, RuntimeSchemaData, SchemaDeterminerError}

class BasedOnVersionAvroSchemaDeterminer(schemaRegistryClient: SchemaRegistryClient,
                                         topic: String,
                                         versionOption: SchemaVersionOption,
                                         isKey: Boolean) extends AvroSchemaDeterminer {

  override def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, RuntimeSchemaData] = {
    val version = versionOption match {
      case ExistingSchemaVersion(v) => Some(v)
      case LatestSchemaVersion => None
    }
    schemaRegistryClient
      .getFreshSchema(topic, version, isKey = isKey)
      .leftMap(err => new SchemaDeterminerError(s"Fetching schema error for topic: $topic, version: $versionOption", err))
      .map(withMetadata => RuntimeSchemaData(withMetadata.schema, Some(withMetadata.id)))
  }

}

class BasedOnVersionWithFallbackAvroSchemaDeterminer(schemaRegistryClient: SchemaRegistryClient,
                                                     topic: String,
                                                     versionOption: SchemaVersionOption,
                                                     isKey: Boolean,
                                                     schema: Schema
                                                    ) extends AvroSchemaDeterminer {

  override def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, RuntimeSchemaData] = {
    val version = versionOption match {
      case ExistingSchemaVersion(v) => Some(v)
      case LatestSchemaVersion => None
    }
    schemaRegistryClient
      .getFreshSchema(topic, version, isKey = isKey)
      .map(withMetadata => RuntimeSchemaData(withMetadata.schema, Some(withMetadata.id)))
      .orElse(Valid(RuntimeSchemaData(schema, None)))
  }

}
