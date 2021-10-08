package pl.touk.nussknacker.engine.avro.schemaregistry

import pl.touk.nussknacker.engine.kafka.KafkaConfig

trait SchemaRegistryClientFactory extends Serializable {
  def create(kafkaConfig: KafkaConfig): SchemaRegistryClient
}
