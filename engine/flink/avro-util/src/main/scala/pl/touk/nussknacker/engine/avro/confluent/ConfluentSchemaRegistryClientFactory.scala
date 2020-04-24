package pl.touk.nussknacker.engine.avro.confluent

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => ConfluenticSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.confluent.ConfluentSchemaRegistryClientFactory.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.avro.{SchemaRegistryClient, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

trait ConfluentSchemaRegistryClientFactory extends SchemaRegistryClientFactory {
  override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient
}

object ConfluentSchemaRegistryClientFactory {
  type ConfluentSchemaRegistryClient = SchemaRegistryClient with ConfluenticSchemaRegistryClient
}
