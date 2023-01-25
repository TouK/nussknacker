package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.schemaid.{SchemaIdFromNuHeadersAndPotentiallyConfluentPayload, SchemaIdFromPayloadInConfluentFormat}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.{KafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory, KafkaSchemaRegistryBasedValueSerializationSchemaFactory}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ChainedSchemaIdFromMessageExtractor, SchemaBasedSerdeProvider, SchemaRegistryClientFactory}

object UniversalSchemaBasedSerdeProvider {

  // SchemaId can be obtain in several ways. Precedent:
  // * from kafka header - it is our own, Nussknacker headers standard format: key.schemaId and value.schemaId headers
  // * from payload serialized in 'Confluent way' ([magicbyte][schemaid][payload])
  // * (fallback) from source editor version param - this is just an assumption we make (when processing no-schemed-data, everything can happen)
  val schemaIdFromMessageExtractor = new ChainedSchemaIdFromMessageExtractor(List(
    SchemaIdFromNuHeadersAndPotentiallyConfluentPayload,
    SchemaIdFromPayloadInConfluentFormat))

  def create(schemaRegistryClientFactory: SchemaRegistryClientFactory): SchemaBasedSerdeProvider = {
    SchemaBasedSerdeProvider(
      new KafkaSchemaRegistryBasedValueSerializationSchemaFactory(schemaRegistryClientFactory, UniversalSerializerFactory),
      new KafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory(
        schemaRegistryClientFactory,
        new UniversalKafkaDeserializerFactory(schemaIdFromMessageExtractor)),
      new UniversalToJsonFormatterFactory(schemaRegistryClientFactory, schemaIdFromMessageExtractor),
      UniversalSchemaValidator
    )
  }

}
