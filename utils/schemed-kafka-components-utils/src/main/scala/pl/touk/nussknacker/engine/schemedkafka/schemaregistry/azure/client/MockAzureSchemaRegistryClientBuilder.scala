package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.client

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName

import scala.collection.mutable.ListBuffer

class MockAzureSchemaRegistryClientBuilder {

  private val registry: ListBuffer[RegistryItem] = ListBuffer()

  def register(
      topic: String,
      schema: Schema,
      version: Int,
      isKey: Boolean,
      schemaId: String = null
  ): MockAzureSchemaRegistryClientBuilder = {
    registry.append(RegistryItem(topic, new AvroSchema(schema), version, isKey, schemaId))
    this
  }

  def build: MockAzureSchemaRegistryClient = {
    val client = new MockAzureSchemaRegistryClient
    registry.foreach(reg => register(client, reg))
    client
  }

  private def register(mockSchemaRegistry: MockAzureSchemaRegistryClient, item: RegistryItem): Unit = {
    mockSchemaRegistry.register(UnspecializedTopicName(item.topic), item.isKey, item.schema, item.version, item.id)
  }

}

private[client] case class RegistryItem(topic: String, schema: ParsedSchema, version: Int, isKey: Boolean, id: String)
