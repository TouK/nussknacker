package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  ChainedSchemaIdFromMessageExtractor,
  SchemaBasedSerdeProvider,
  SchemaRegistryClient,
  SchemaRegistryClientFactory
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.AzureSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.schemaid.SchemaIdFromAzureHeader
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.schemaid.{
  SchemaIdFromNuHeadersPotentiallyShiftingConfluentPayload,
  SchemaIdFromPayloadInConfluentFormat
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.{
  KafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory,
  KafkaSchemaRegistryBasedValueSerializationSchemaFactory
}

object UniversalSchemaBasedSerdeProvider {

  def create(schemaRegistryClientFactory: SchemaRegistryClientFactory): SchemaBasedSerdeProvider = {
    SchemaBasedSerdeProvider(
      new KafkaSchemaRegistryBasedValueSerializationSchemaFactory(
        schemaRegistryClientFactory,
        UniversalSerializerFactory
      ),
      new KafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory(
        schemaRegistryClientFactory,
        new UniversalKafkaDeserializerFactory(createSchemaIdFromMessageExtractor)
      ),
      new UniversalToJsonFormatterFactory(schemaRegistryClientFactory, createSchemaIdFromMessageExtractor),
      UniversalSchemaValidator
    )
  }

  def createSchemaIdFromMessageExtractor(
      schemaRegistryClient: SchemaRegistryClient
  ): ChainedSchemaIdFromMessageExtractor = {
    val isConfluent = schemaRegistryClient.isInstanceOf[ConfluentSchemaRegistryClient]
    val isAzure     = schemaRegistryClient.isInstanceOf[AzureSchemaRegistryClient]
    createSchemaIdFromMessageExtractor(isConfluent, isAzure)
  }

  // SchemaId can be obtained in several ways. Precedent:
  // * from nu kafka headers - it is our own, Nussknacker headers standard format: key.schemaId and value.schemaId headers
  // * from azure header - content-type: avro/binary+schemaId (only value schema ids are supported)
  // * from payload serialized in 'Confluent way' ([magicbyte][schemaid][payload])
  // * (fallback) from source editor version param - this is just an assumption we make (when processing no-schemed-data, everything can happen)
  private[schemaregistry] def createSchemaIdFromMessageExtractor(
      isConfluent: Boolean,
      isAzure: Boolean
  ): ChainedSchemaIdFromMessageExtractor = {
    val chain = new SchemaIdFromNuHeadersPotentiallyShiftingConfluentPayload(
      intSchemaId = isConfluent,
      potentiallyShiftConfluentPayload = isConfluent
    ) ::
      List(SchemaIdFromAzureHeader).filter(_ => isAzure) :::
      List(SchemaIdFromPayloadInConfluentFormat).filter(_ => isConfluent)
    new ChainedSchemaIdFromMessageExtractor(chain)
  }

}
