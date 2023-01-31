package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.schemaid.{SchemaIdFromNuHeadersPotentiallyShiftingConfluentPayload, SchemaIdFromPayloadInConfluentFormat}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.{KafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory, KafkaSchemaRegistryBasedValueSerializationSchemaFactory}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ChainedSchemaIdFromMessageExtractor, SchemaBasedSerdeProvider, SchemaRegistryClient, SchemaRegistryClientFactory}

object UniversalSchemaBasedSerdeProvider {

  def create(schemaRegistryClientFactory: SchemaRegistryClientFactory): SchemaBasedSerdeProvider = {
    SchemaBasedSerdeProvider(
      new KafkaSchemaRegistryBasedValueSerializationSchemaFactory(schemaRegistryClientFactory, UniversalSerializerFactory),
      new KafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory(
        schemaRegistryClientFactory,
        new UniversalKafkaDeserializerFactory(createSchemaIdFromMessageExtractor)),
      new UniversalToJsonFormatterFactory(schemaRegistryClientFactory, createSchemaIdFromMessageExtractor),
      UniversalSchemaValidator
    )
  }

  private def createSchemaIdFromMessageExtractor(schemaRegistryClient: SchemaRegistryClient): ChainedSchemaIdFromMessageExtractor = {
    val isConfluent = schemaRegistryClient.isInstanceOf[ConfluentSchemaRegistryClient]
    createSchemaIdFromMessageExtractor(isConfluent)
  }

  // SchemaId can be obtain in several ways. Precedent:
  // * from nu kafka headers - it is our own, Nussknacker headers standard format: key.schemaId and value.schemaId headers
  // * from payload serialized in 'Confluent way' ([magicbyte][schemaid][payload])
  // * (fallback) from source editor version param - this is just an assumption we make (when processing no-schemed-data, everything can happen)
  private[schemaregistry] def createSchemaIdFromMessageExtractor(isConfluent: Boolean): ChainedSchemaIdFromMessageExtractor = {
    val chain = new SchemaIdFromNuHeadersPotentiallyShiftingConfluentPayload(intSchemaId = isConfluent, potentiallyShiftConfluentPayload = isConfluent) ::
      List(SchemaIdFromPayloadInConfluentFormat).filter(_ => isConfluent)
    new ChainedSchemaIdFromMessageExtractor(chain)
  }

}
