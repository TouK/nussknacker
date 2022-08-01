package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import pl.touk.nussknacker.engine.kafka.{KafkaConfig, SchemaRegistryClientKafkaConfig}

/**
 * MockSchemaRegistryClient must be passed by name, because schemaRegistryMockClient is not serializable.
 */
class MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient: => MockSchemaRegistryClient)
  extends ConfluentSchemaRegistryClientFactory {

  override def create(config: SchemaRegistryClientKafkaConfig): ConfluentSchemaRegistryClient = {
    new DefaultConfluentSchemaRegistryClient(schemaRegistryMockClient, config)
  }

}
