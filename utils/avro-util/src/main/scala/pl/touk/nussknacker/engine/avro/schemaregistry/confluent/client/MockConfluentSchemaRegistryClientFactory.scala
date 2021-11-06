package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import pl.touk.nussknacker.engine.kafka.KafkaConfig

/**
 * MockSchemaRegistryClient must be passed by name, because schemaRegistryMockClient is not serializable.
 */
class MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient: => MockSchemaRegistryClient)
  extends ConfluentSchemaRegistryClientFactory {

  override def create(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient = {
    new DefaultConfluentSchemaRegistryClient(schemaRegistryMockClient)
  }

}
