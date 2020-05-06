package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.MockConfluentSchemaRegistryClient.RegistryItem
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.collection.mutable.ListBuffer

class MockConfluentSchemaRegistryClientFactory(data: List[RegistryItem]) extends ConfluentSchemaRegistryClientFactory {
  def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient =
    new MockConfluentSchemaRegistryClient(data)
}

class MockConfluentSchemaRegistryClientFactoryBuilder {

  private val registry: ListBuffer[RegistryItem] = ListBuffer()

  def register(topic: String, schema: Schema, version: Int, isKey: Boolean): MockConfluentSchemaRegistryClientFactoryBuilder = {
    registry.append(RegistryItem(topic, schema, version, isKey))
    this
  }

  def register(topic: String, schema: String, version: Int, isKey: Boolean): MockConfluentSchemaRegistryClientFactoryBuilder = {
    registry.append(RegistryItem(topic,schema, version, isKey))
    this
  }

  def build: MockConfluentSchemaRegistryClientFactory =
    new MockConfluentSchemaRegistryClientFactory(registry.toList)
}
