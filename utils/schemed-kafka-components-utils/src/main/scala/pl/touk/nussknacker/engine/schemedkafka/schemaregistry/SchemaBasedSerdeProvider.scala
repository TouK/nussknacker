package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import pl.touk.nussknacker.engine.schemedkafka.schema.SchemaValidator
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalToJsonFormatterFactory
import pl.touk.nussknacker.engine.schemedkafka.serialization.{
  KafkaSchemaBasedKeyValueDeserializationSchemaFactory,
  KafkaSchemaBasedSerializationSchemaFactory
}

case class SchemaBasedSerdeProvider(
    serializationSchemaFactory: KafkaSchemaBasedSerializationSchemaFactory,
    deserializationSchemaFactory: KafkaSchemaBasedKeyValueDeserializationSchemaFactory,
    recordFormatterFactory: UniversalToJsonFormatterFactory,
    schemaValidator: SchemaValidator
)
