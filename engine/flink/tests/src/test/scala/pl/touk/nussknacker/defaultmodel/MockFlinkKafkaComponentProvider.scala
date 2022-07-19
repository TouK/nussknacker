package pl.touk.nussknacker.defaultmodel

import pl.touk.nussknacker.defaultmodel.MockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaBasedMessagesSerdeProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkKafkaComponentProvider

class MockFlinkKafkaComponentProvider extends FlinkKafkaComponentProvider {

  override def providerName: String = "mockKafka"

  override protected def schemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)

}
