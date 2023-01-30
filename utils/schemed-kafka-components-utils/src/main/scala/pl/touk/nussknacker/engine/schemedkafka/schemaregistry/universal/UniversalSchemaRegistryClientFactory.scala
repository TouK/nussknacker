package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClient, SchemaRegistryClientFactory}

object UniversalSchemaRegistryClientFactory extends UniversalSchemaRegistryClientFactory

class UniversalSchemaRegistryClientFactory extends SchemaRegistryClientFactory {

  override type SchemaRegistryClientT = SchemaRegistryClient

  override def create(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClientT =
    createConfluentFactory.create(config)

  private def createConfluentFactory: SchemaRegistryClientFactory = {
    CachedConfluentSchemaRegistryClientFactory
  }

}
