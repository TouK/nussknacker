package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  SchemaRegistryClientFactoryWithRegistration,
  SchemaRegistryClientWithRegistration
}

/**
 * SchemaRegistryClient must be passed by name, because schemaRegistryMockClient is not serializable.
 * This class do not use caching - mocks are already fast
 */
class MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient: => CSchemaRegistryClient)
    extends SchemaRegistryClientFactoryWithRegistration {

  override type SchemaRegistryClientT = SchemaRegistryClientWithRegistration with ConfluentSchemaRegistryClient

  override def createOnConfigWithSchemaUrl(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClientT = {
    new DefaultConfluentSchemaRegistryClient(schemaRegistryMockClient)
  }

}
