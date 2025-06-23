package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import cats.data.Validated
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.{RuntimeSchemaData, SchemaDeterminerError}

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
