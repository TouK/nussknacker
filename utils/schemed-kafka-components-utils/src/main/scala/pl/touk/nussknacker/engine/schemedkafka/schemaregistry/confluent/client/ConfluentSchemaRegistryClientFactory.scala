package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client

import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, SchemaRegistryClientKafkaConfig}

trait ConfluentSchemaRegistryClientFactory extends SchemaRegistryClientFactory {
  override def create(config: KafkaConfig): ConfluentSchemaRegistryClient = create(config.schemaRegistryClientKafkaConfig)

  override def create(config: SchemaRegistryClientKafkaConfig): ConfluentSchemaRegistryClient
}
