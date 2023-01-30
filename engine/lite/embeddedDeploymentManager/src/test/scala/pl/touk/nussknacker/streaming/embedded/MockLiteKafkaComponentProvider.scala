package pl.touk.nussknacker.streaming.embedded

import pl.touk.nussknacker.engine.lite.components.LiteKafkaComponentProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.streaming.embedded.MockSchemaRegistry.schemaRegistryMockClient

class MockLiteKafkaComponentProvider extends LiteKafkaComponentProvider(MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)) {

  override def providerName: String = "mockKafka"

}

object MockSchemaRegistry extends Serializable {

  val schemaRegistryMockClient: MockSchemaRegistryClient = new MockSchemaRegistryClient

}