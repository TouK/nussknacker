package pl.touk.nussknacker.defaultmodel

import pl.touk.nussknacker.defaultmodel.MockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkKafkaComponentProvider
import pl.touk.nussknacker.engine.kafka.SchemaRegistryCacheConfig

class MockFlinkKafkaComponentProvider extends FlinkKafkaComponentProvider {

  override def providerName: String = "mockKafka"

  override protected def createAvroSchemaRegistryProvider(schemaRegistryCacheConfig: SchemaRegistryCacheConfig): SchemaRegistryProvider =
    ConfluentSchemaRegistryProvider.avroPayload(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))

  override protected def createJsonSchemaRegistryProvider(schemaRegistryCacheConfig: SchemaRegistryCacheConfig): SchemaRegistryProvider =
    ConfluentSchemaRegistryProvider.jsonPayload(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))

}
