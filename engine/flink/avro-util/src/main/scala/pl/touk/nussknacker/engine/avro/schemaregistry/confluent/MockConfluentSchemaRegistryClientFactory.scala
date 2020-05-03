package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryClientFactory.TypedConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.collection.mutable.ListBuffer

class MockConfluentSchemaRegistryClientFactory(data: List[(String, Schema, Boolean)]) extends ConfluentSchemaRegistryClientFactory {
  def createSchemaRegistryClient(kafkaConfig: KafkaConfig): TypedConfluentSchemaRegistryClient = {
    val mockSchemaRegistryClient = new MockSchemaRegistryClient with ConfluentSchemaRegistryClient
    data.foreach(item => register(mockSchemaRegistryClient, item))
    mockSchemaRegistryClient
  }

  private def register(mockSchemaRegistry: MockSchemaRegistryClient, item: (String, Schema, Boolean)): MockSchemaRegistryClient = {
    val subject = item._1 + "-" + (if (item._3) "key" else "value")
    mockSchemaRegistry.register(subject, item._2)
    mockSchemaRegistry
  }
}

class MockConfluentSchemaRegistryClientFactoryBuilder {
  private val registry: ListBuffer[(String, Schema, Boolean)] = ListBuffer()

  def register(topic: String, schema: Schema, isKey: Boolean = false): MockConfluentSchemaRegistryClientFactoryBuilder = {
    registry.append((topic, schema, isKey))
    this
  }

  def registerStringSchema(topic: String, schema: String, isKey: Boolean = false): MockConfluentSchemaRegistryClientFactoryBuilder = {
    registry.append((topic, AvroUtils.createSchema(schema), isKey))
    this
  }

  def build: MockConfluentSchemaRegistryClientFactory =
    new MockConfluentSchemaRegistryClientFactory(registry.toList)
}
