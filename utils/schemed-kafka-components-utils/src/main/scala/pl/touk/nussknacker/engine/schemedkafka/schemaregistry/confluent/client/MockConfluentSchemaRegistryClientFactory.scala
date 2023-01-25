package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client

import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClient, SchemaRegistryClientFactory}

/**
 * MockSchemaRegistryClient must be passed by name, because schemaRegistryMockClient is not serializable.
 */
class MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient: => MockSchemaRegistryClient)
  extends SchemaRegistryClientFactory {

  override def create(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClient = {
    new DefaultConfluentSchemaRegistryClient(schemaRegistryMockClient, config)
  }

}
