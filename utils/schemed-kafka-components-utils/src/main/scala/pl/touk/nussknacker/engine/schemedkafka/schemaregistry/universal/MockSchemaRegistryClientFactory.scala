package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactoryWithRegistration
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.client.{
  AzureSchemaRegistryClient,
  MockAzureSchemaRegistryClientFactory
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory

object MockSchemaRegistryClientFactory {

  def confluentBased(schemaRegistryMockClient: => CSchemaRegistryClient): SchemaRegistryClientFactoryWithRegistration =
    new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)

  def azureBased(schemaRegistryMockClient: => AzureSchemaRegistryClient): SchemaRegistryClientFactoryWithRegistration =
    new MockAzureSchemaRegistryClientFactory(schemaRegistryMockClient)

}
