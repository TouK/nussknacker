package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import pl.touk.nussknacker.engine.kafka.{KafkaUtils, SchemaRegistryClientKafkaConfig}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.AzureSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{EmptySchemaRegistry, SchemaRegistryClient, SchemaRegistryClientFactory}

object UniversalSchemaRegistryClientFactory extends UniversalSchemaRegistryClientFactory

class UniversalSchemaRegistryClientFactory extends SchemaRegistryClientFactory {

  override type SchemaRegistryClientT = SchemaRegistryClient

  override def create(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClientT = {
    val maybeUrl = config.kafkaProperties.get("schema.registry.url")
    // TODO_PAWEL 1 to tylko na test tak zrobione
    if (maybeUrl.isDefined && maybeUrl.get == "not_used") {
      EmptySchemaRegistry
    }
    else {
      if (maybeUrl.exists(_.endsWith(KafkaUtils.azureEventHubsUrl))) {
        AzureSchemaRegistryClientFactory.create(config)
      } else {
        CachedConfluentSchemaRegistryClientFactory.create(config)
      }
    }
  }

}
