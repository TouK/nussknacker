package pl.touk.nussknacker.engine.schemedkafka

import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.KafkaSchemaRegistryBasedValueSerializationSchemaFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaBasedSerdeProvider.createSchemaIdFromMessageExtractor
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{
  UniversalKafkaDeserializerFactory,
  UniversalSchemaValidator,
  UniversalSerializerFactory,
  UniversalToJsonFormatterFactory
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.source.flink.FlinkKafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory

object FlinkUniversalSchemaBasedSerdeProvider {

  def create(schemaRegistryClientFactory: SchemaRegistryClientFactory): SchemaBasedSerdeProvider = {
    SchemaBasedSerdeProvider(
      new KafkaSchemaRegistryBasedValueSerializationSchemaFactory(
        schemaRegistryClientFactory,
        UniversalSerializerFactory
      ),
      new FlinkKafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory(
        schemaRegistryClientFactory,
        new UniversalKafkaDeserializerFactory(createSchemaIdFromMessageExtractor)
      ),
      new UniversalToJsonFormatterFactory(schemaRegistryClientFactory, createSchemaIdFromMessageExtractor),
      UniversalSchemaValidator
    )
  }

}
