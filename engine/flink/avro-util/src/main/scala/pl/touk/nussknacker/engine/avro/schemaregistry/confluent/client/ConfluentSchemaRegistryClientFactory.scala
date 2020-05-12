package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import pl.touk.nussknacker.engine.kafka.KafkaConfig

trait ConfluentSchemaRegistryClientFactory extends Serializable {
  def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient
}
