package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import pl.touk.nussknacker.engine.kafka.{KafkaConfig, SchemaRegistryClientKafkaConfig}

trait SchemaRegistryClientFactory extends Serializable {
  def create(config: KafkaConfig): SchemaRegistryClient = create(config.schemaRegistryClientKafkaConfig)

  def create(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClient
}
