package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryClientFactory.TypedConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.collection.mutable.ListBuffer

class MockConfluentSchemaRegistryClientFactory(data: List[RegistryItem]) extends ConfluentSchemaRegistryClientFactory {
  def createSchemaRegistryClient(kafkaConfig: KafkaConfig): TypedConfluentSchemaRegistryClient = {
    val mockSchemaRegistryClient = new MockSchemaRegistryClient with ConfluentSchemaRegistryClient
    data.foreach(item => register(mockSchemaRegistryClient, item))
    mockSchemaRegistryClient
  }

  private def register(mockSchemaRegistry: MockSchemaRegistryClient, item: RegistryItem): MockSchemaRegistryClient = {
    val subject = item.topic + "-" + (if (item.isKey) "key" else "value")
    mockSchemaRegistry.register(subject, item.schema, item.version, -1)
    mockSchemaRegistry
  }
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

case class RegistryItem(topic: String, schema: Schema, version: Int, isKey: Boolean, id: Int)

object RegistryItem {
  def apply(topic: String, schema: String, version: Int, isKey: Boolean): RegistryItem =
    new RegistryItem(topic, AvroUtils.createSchema(schema), version, isKey, -1)

  def apply(topic: String, schema: Schema, version: Int, isKey: Boolean): RegistryItem =
    new RegistryItem(topic, schema, version, isKey, -1)

  def apply(topic: String, schema: Schema, version: Int): RegistryItem =
    new RegistryItem(topic, schema, version, false, -1)
}
