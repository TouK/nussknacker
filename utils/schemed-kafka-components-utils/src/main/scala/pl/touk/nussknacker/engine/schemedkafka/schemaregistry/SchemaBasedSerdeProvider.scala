package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{
  UniversalKafkaSerializationSchemaFactory,
  UniversalToJsonFormatterFactory
}
import pl.touk.nussknacker.engine.schemedkafka.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory

case class SchemaBasedSerdeProvider(
    serializationSchemaFactory: UniversalKafkaSerializationSchemaFactory,
    deserializationSchemaFactory: KafkaSchemaBasedKeyValueDeserializationSchemaFactory,
    recordFormatterFactory: UniversalToJsonFormatterFactory,
)
