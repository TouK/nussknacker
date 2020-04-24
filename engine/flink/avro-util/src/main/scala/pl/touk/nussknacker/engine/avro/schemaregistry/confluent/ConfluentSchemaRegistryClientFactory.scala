package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => ConfluenticSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryClientFactory.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryClient, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

trait ConfluentSchemaRegistryClientFactory extends SchemaRegistryClientFactory {
  override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient
}

object ConfluentSchemaRegistryClientFactory {
  type ConfluentSchemaRegistryClient = SchemaRegistryClient with ConfluenticSchemaRegistryClient
}
