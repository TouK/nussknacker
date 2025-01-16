package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import pl.touk.nussknacker.engine.kafka.{KafkaUtils, SchemaRegistryClientKafkaConfig}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.AzureSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClient, SchemaRegistryClientFactory}

object UniversalSchemaRegistryClientFactory extends UniversalSchemaRegistryClientFactory

class UniversalSchemaRegistryClientFactory extends SchemaRegistryClientFactory {

  override type SchemaRegistryClientT = SchemaRegistryClient

  override def createOnConfigWithSchemaUrl(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClientT = {
    // TODO_PAWEL wczesniej nigdy nullem nie byl..., no w tym miejscu koud tez nigdy nie bedzie
    if (config.kafkaProperties.get("schema.registry.url").exists(_.endsWith(KafkaUtils.azureEventHubsUrl))) {
      AzureSchemaRegistryClientFactory.create(config)
    } else {
      CachedConfluentSchemaRegistryClientFactory.create(config)
    }
  }

}
