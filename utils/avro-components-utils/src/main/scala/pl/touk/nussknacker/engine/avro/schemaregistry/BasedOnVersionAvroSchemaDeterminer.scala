package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, UniversalRuntimeSchemaData, RuntimeSchemaData, SchemaDeterminerError, UniversalSchemaDeterminer}

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
      .map(withMetadata => RuntimeSchemaData(withMetadata.schema.rawSchema().asInstanceOf[Schema], Some(withMetadata.id)))
  }

}

class BasedOnVersionSchemaDeterminer(schemaRegistryClient: SchemaRegistryClient,
                                     topic: String,
                                     versionOption: SchemaVersionOption,
                                     isKey: Boolean) extends UniversalSchemaDeterminer {

  override def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, UniversalRuntimeSchemaData] = {
    val version = versionOption match {
      case ExistingSchemaVersion(v) => Some(v)
      case LatestSchemaVersion => None
    }
    schemaRegistryClient
      .getFreshSchema(topic, version, isKey = isKey)
      .leftMap(err => new SchemaDeterminerError(s"Fetching schema error for topic: $topic, version: $versionOption", err))
      //.map(withMetadata => RuntimeSchemaData(withMetadata.schema.rawSchema().asInstanceOf[Schema], Some(withMetadata.id)))
      .map(withMetadata => UniversalRuntimeSchemaData(withMetadata.schema, Some(withMetadata.id)))
  }

}