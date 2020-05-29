package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils

import scala.collection.mutable.ListBuffer

class MockConfluentSchemaRegistryClientBuilder {

  private val registry: ListBuffer[RegistryItem] = ListBuffer()

  def register(topic: String, schema: Schema, version: Int, isKey: Boolean): MockConfluentSchemaRegistryClientBuilder = {
    registry.append(RegistryItem(topic, schema, version, isKey))
    this
  }

  def register(topic: String, schema: String, version: Int, isKey: Boolean): MockConfluentSchemaRegistryClientBuilder = {
    registry.append(RegistryItem(topic,schema, version, isKey))
    this
  }

  private def register(mockSchemaRegistry: MockSchemaRegistryClient, item: RegistryItem): Int = {
    val subject = item.topic + "-" + (if (item.isKey) "key" else "value")
    mockSchemaRegistry.register(subject, item.schema, item.version, item.id)
  }

  def build: MockSchemaRegistryClient = {
    val client = new MockSchemaRegistryClient
    registry.foreach(reg => register(client, reg))
    client
  }
}

private[client] case class RegistryItem(topic: String, schema: Schema, version: Int, isKey: Boolean, id: Int)

private[client] object RegistryItem {

  //Default value for autoincrement mock id
  private val AutoIncId = -1

  def apply(topic: String, schema: String, version: Int, isKey: Boolean): RegistryItem =
    new RegistryItem(topic, AvroUtils.parseSchema(schema), version, isKey, AutoIncId)

  def apply(topic: String, schema: Schema, version: Int, isKey: Boolean): RegistryItem =
    new RegistryItem(topic, schema, version, isKey, AutoIncId)
}
