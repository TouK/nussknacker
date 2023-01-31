package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import pl.touk.nussknacker.engine.kafka.RecordFormatterFactory
import pl.touk.nussknacker.engine.schemedkafka.schema.SchemaValidator
import pl.touk.nussknacker.engine.schemedkafka.serialization.{KafkaSchemaBasedDeserializationSchemaFactory, KafkaSchemaBasedSerializationSchemaFactory}

case class SchemaBasedSerdeProvider(serializationSchemaFactory: KafkaSchemaBasedSerializationSchemaFactory,
                                    deserializationSchemaFactory: KafkaSchemaBasedDeserializationSchemaFactory,
                                    recordFormatterFactory: RecordFormatterFactory,
                                    schemaValidator: SchemaValidator)