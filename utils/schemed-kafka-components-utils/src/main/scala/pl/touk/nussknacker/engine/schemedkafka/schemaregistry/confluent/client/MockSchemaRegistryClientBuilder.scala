package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.avro.Schema
import org.everit.json.schema.{Schema => JsonSchema}
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils

import scala.collection.mutable.ListBuffer

class MockConfluentSchemaRegistryClientBuilder {

  private val registry: ListBuffer[RegistryItem] = ListBuffer()

  // Default value for autoincrement mock id
  private val AutoIncId = -1

  def register(
      topic: String,
      schema: Schema,
      version: Int,
      isKey: Boolean
  ): MockConfluentSchemaRegistryClientBuilder = registerWithId(topic, schema, version, isKey, AutoIncId)

  def registerWithId(
      topic: String,
      schema: Schema,
      version: Int,
      isKey: Boolean,
      schemaId: Int
  ): MockConfluentSchemaRegistryClientBuilder = {
    val avroSchema = ConfluentUtils.convertToAvroSchema(schema)
    registry.append(RegistryItem(topic, avroSchema, version, isKey, schemaId))
    this
  }

  def register(
      topic: String,
      schema: JsonSchema,
      version: Int,
      isKey: Boolean
  ): MockConfluentSchemaRegistryClientBuilder = registerWithId(topic, schema, version, isKey, AutoIncId)

  def registerWithId(
      topic: String,
      schema: JsonSchema,
      version: Int,
      isKey: Boolean,
      schemaId: Int
  ): MockConfluentSchemaRegistryClientBuilder = {
    val avroSchema = ConfluentUtils.convertToJsonSchema(schema)
    registry.append(RegistryItem(topic, avroSchema, version, isKey, schemaId))
    this
  }

  def build: MockSchemaRegistryClient = {
    val client = new MockSchemaRegistryClient
    registry.foreach(reg => register(client, reg))
    client
  }

  private def register(mockSchemaRegistry: MockSchemaRegistryClient, item: RegistryItem): Int = {
    val subject = ConfluentUtils.topicSubject(UnspecializedTopicName(item.topic), item.isKey)
    mockSchemaRegistry.register(subject, item.schema, item.version, item.id)
  }

}

private[client] case class RegistryItem(topic: String, schema: ParsedSchema, version: Int, isKey: Boolean, id: Int)
