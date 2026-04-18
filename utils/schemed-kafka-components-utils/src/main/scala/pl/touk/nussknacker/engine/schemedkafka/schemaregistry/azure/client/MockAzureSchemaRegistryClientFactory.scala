package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.client

import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactoryWithRegistration

class MockAzureSchemaRegistryClientFactory(schemaRegistryMockClient: => AzureSchemaRegistryClient)
    extends SchemaRegistryClientFactoryWithRegistration {

  override type SchemaRegistryClientT = AzureSchemaRegistryClient

  override def create(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClientT = {
    schemaRegistryMockClient
  }

}
