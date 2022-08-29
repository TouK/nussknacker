package pl.touk.nussknacker.streaming.embedded

import pl.touk.nussknacker.engine.lite.components.LiteKafkaComponentProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.streaming.embedded.MockSchemaRegistry.schemaRegistryMockClient

class MockLiteKafkaComponentProvider extends LiteKafkaComponentProvider(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)) {

  override def providerName: String = "mockKafka"

}

object MockSchemaRegistry extends Serializable {

  val schemaRegistryMockClient: MockSchemaRegistryClient = new MockSchemaRegistryClient

}