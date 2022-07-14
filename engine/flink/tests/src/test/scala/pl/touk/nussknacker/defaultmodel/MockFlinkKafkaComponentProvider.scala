package pl.touk.nussknacker.defaultmodel

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import pl.touk.nussknacker.defaultmodel.MockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkKafkaComponentProvider

class MockFlinkKafkaComponentProvider extends FlinkKafkaComponentProvider {

  override def providerName: String = "mockKafka"

  override protected def createAvroSchemaRegistryProvider: SchemaRegistryProvider[AvroSchema] =
    ConfluentSchemaRegistryProvider.avroSchemaAvroPayload(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))

  override protected def createJsonSchemaRegistryProvider: SchemaRegistryProvider[AvroSchema] =
    ConfluentSchemaRegistryProvider.avroSchemaJsonPayload(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))

}
