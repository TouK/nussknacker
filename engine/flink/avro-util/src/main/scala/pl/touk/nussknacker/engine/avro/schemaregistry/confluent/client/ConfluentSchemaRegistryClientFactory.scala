package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig

trait ConfluentSchemaRegistryClientFactory extends SchemaRegistryClientFactory {
  override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient
}
