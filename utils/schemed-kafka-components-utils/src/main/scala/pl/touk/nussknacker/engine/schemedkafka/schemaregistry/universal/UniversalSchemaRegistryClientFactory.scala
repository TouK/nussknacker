package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import pl.touk.nussknacker.engine.kafka.{KafkaUtils, SchemaRegistryClientKafkaConfig}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  EmptySchemaRegistry,
  SchemaRegistryClient,
  SchemaRegistryClientFactory
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.AzureSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClientFactory

object UniversalSchemaRegistryClientFactory extends UniversalSchemaRegistryClientFactory

class UniversalSchemaRegistryClientFactory extends SchemaRegistryClientFactory {

  override type SchemaRegistryClientT = SchemaRegistryClient

  override def create(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClientT = {
    config.kafkaProperties.get("schema.registry.url") match {
      case None => EmptySchemaRegistry
      case Some(url) =>
        if (url.endsWith(KafkaUtils.azureEventHubsUrl)) {
          AzureSchemaRegistryClientFactory.create(config)
        } else {
          CachedConfluentSchemaRegistryClientFactory.create(config)
        }
    }
  }

}
