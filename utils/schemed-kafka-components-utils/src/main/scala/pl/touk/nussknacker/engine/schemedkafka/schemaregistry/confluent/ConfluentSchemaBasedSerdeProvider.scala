package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent

import pl.touk.nussknacker.engine.schemedkafka.schema.AvroSchemaValidator
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.schemaid.SchemaIdFromPayloadInConfluentFormat
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload.{ConfluentJsonPayloadDeserializerFactory, ConfluentJsonPayloadSerializerFactory}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter.AvroToJsonFormatterFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.json.JsonPayloadToJsonFormatterFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.{KafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory, KafkaSchemaRegistryBasedValueSerializationSchemaFactory}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryClientFactory}

object ConfluentSchemaBasedSerdeProvider extends Serializable {

  def avroPayload(schemaRegistryClientFactory: SchemaRegistryClientFactory): SchemaBasedSerdeProvider = {
    val serializerFactory = ConfluentAvroSerializerFactory
    SchemaBasedSerdeProvider(
      new KafkaSchemaRegistryBasedValueSerializationSchemaFactory(schemaRegistryClientFactory, serializerFactory),
      new KafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory(schemaRegistryClientFactory, ConfluentAvroDeserializerFactory),
      new AvroToJsonFormatterFactory(
        schemaRegistryClientFactory,
        SchemaIdFromPayloadInConfluentFormat,
        serializerFactory),
      AvroSchemaValidator
    )
  }

  def jsonPayload(schemaRegistryClientFactory: SchemaRegistryClientFactory): SchemaBasedSerdeProvider = {
    SchemaBasedSerdeProvider(
      new KafkaSchemaRegistryBasedValueSerializationSchemaFactory(schemaRegistryClientFactory, ConfluentJsonPayloadSerializerFactory),
      new KafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory(schemaRegistryClientFactory, ConfluentJsonPayloadDeserializerFactory),
      new JsonPayloadToJsonFormatterFactory,
      AvroSchemaValidator
    )
  }

}
