package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import pl.touk.nussknacker.engine.schemedkafka.schema.SchemaValidator
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalToJsonFormatterFactory
import pl.touk.nussknacker.engine.schemedkafka.serialization.{
  KafkaSchemaBasedDeserializationSchemaFactory,
  KafkaSchemaBasedSerializationSchemaFactory
}

case class SchemaBasedSerdeProvider(
    serializationSchemaFactory: KafkaSchemaBasedSerializationSchemaFactory,
    deserializationSchemaFactory: KafkaSchemaBasedDeserializationSchemaFactory,
    recordFormatterFactory: UniversalToJsonFormatterFactory,
    schemaValidator: SchemaValidator
)
