package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import pl.touk.nussknacker.engine.kafka.{KafkaConfig, SchemaRegistryClientKafkaConfig}

trait SchemaRegistryClientFactory extends Serializable {

  type SchemaRegistryClientT <: SchemaRegistryClient
  def create(config: KafkaConfig): SchemaRegistryClientT = create(config.schemaRegistryClientKafkaConfig)

  def create(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClientT
}

trait SchemaRegistryClientFactoryWithRegistration extends SchemaRegistryClientFactory {

  override type SchemaRegistryClientT <: SchemaRegistryClientWithRegistration

}