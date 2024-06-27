package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.{AvroSchemaDeterminer, RuntimeSchemaData, SchemaDeterminerError}

class BasedOnVersionAvroSchemaDeterminer(
    schemaRegistryClient: SchemaRegistryClient,
    topic: UnspecializedTopicName,
    versionOption: SchemaVersionOption,
    isKey: Boolean
) extends AvroSchemaDeterminer {

  override def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, RuntimeSchemaData[AvroSchema]] = {
    val version = versionOption match {
      case ExistingSchemaVersion(v) => Some(v)
      case LatestSchemaVersion      => None
    }
    schemaRegistryClient
      .getFreshSchema(topic, version, isKey = isKey)
      .leftMap(err =>
        new SchemaDeterminerError(s"Fetching schema error for topic: ${topic.name}, version: $versionOption", err)
      )
      .andThen(withMetadata =>
        withMetadata.schema match {
          case s: AvroSchema => Valid(RuntimeSchemaData(s.rawSchema(), Some(withMetadata.id)))
          case s =>
            Invalid(
              new SchemaDeterminerError(
                s"Avro schema is required, but got ${s.schemaType()}",
                new IllegalArgumentException("")
              )
            )
        }
      )
  }

}

class ParsedSchemaDeterminer(
    schemaRegistryClient: SchemaRegistryClient,
    topic: UnspecializedTopicName,
    versionOption: SchemaVersionOption,
    isKey: Boolean
) {

  def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, RuntimeSchemaData[ParsedSchema]] = {
    val version = versionOption match {
      case ExistingSchemaVersion(v) => Some(v)
      case LatestSchemaVersion      => None
    }
    schemaRegistryClient
      .getFreshSchema(topic, version, isKey = isKey)
      .leftMap(err =>
        new SchemaDeterminerError(s"Fetching schema error for topic: ${topic.name}, version: $versionOption", err)
      )
      .map(withMetadata =>
        RuntimeSchemaData(new NkSerializableParsedSchema[ParsedSchema](withMetadata.schema), Some(withMetadata.id))
      )
  }

}
