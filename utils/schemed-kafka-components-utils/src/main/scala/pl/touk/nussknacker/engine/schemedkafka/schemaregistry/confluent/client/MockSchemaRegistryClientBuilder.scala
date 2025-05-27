package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils

import scala.collection.mutable.ListBuffer

class MockConfluentSchemaRegistryClientBuilder {

  private val registry: ListBuffer[RegistryItem] = ListBuffer()

  def register(
      topic: String,
      schema: Schema,
      version: Int,
      isKey: Boolean
  ): MockConfluentSchemaRegistryClientBuilder = {
    registry.append(RegistryItem(topic, schema, version, isKey))
    this
  }

  def register(
      topic: String,
      schema: String,
      version: Int,
      isKey: Boolean
  ): MockConfluentSchemaRegistryClientBuilder = {
    registry.append(RegistryItem(topic, schema, version, isKey))
    this
  }

  def register(
      topic: String,
      schema: JsonSchema,
      version: Int,
      isKey: Boolean
  ): MockConfluentSchemaRegistryClientBuilder = {
    registry.append(RegistryItem(topic, schema, version, isKey))
    this
  }

  private def register(mockSchemaRegistry: MockSchemaRegistryClient, item: RegistryItem): Int = {
    val subject = ConfluentUtils.topicSubject(UnspecializedTopicName(item.topic), item.isKey)
    mockSchemaRegistry.register(subject, item.schema, item.version, item.id)
  }

  def build: MockSchemaRegistryClient = {
    val client = new MockSchemaRegistryClient
    registry.foreach(reg => register(client, reg))
    client
  }

}

private[client] case class RegistryItem(topic: String, schema: ParsedSchema, version: Int, isKey: Boolean, id: Int)

private[client] object RegistryItem {

  // Default value for autoincrement mock id
  private val AutoIncId = -1

  def apply(topic: String, schema: String, version: Int, isKey: Boolean): RegistryItem =
    new RegistryItem(topic, toParsedSchema(AvroUtils.parseSchema(schema), version), version, isKey, AutoIncId)

  def apply(topic: String, schema: Schema, version: Int, isKey: Boolean): RegistryItem = {
    new RegistryItem(topic, toParsedSchema(schema, version), version, isKey, AutoIncId)
  }

  def apply(topic: String, schema: JsonSchema, version: Int, isKey: Boolean): RegistryItem =
    new RegistryItem(topic, schema, version, isKey, AutoIncId)

  private def toParsedSchema(schema: Schema, version: Int): ParsedSchema = {
    ConfluentUtils.convertToAvroSchema(schema, Some(version))
  }

}
