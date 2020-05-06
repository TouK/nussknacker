package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryError
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.MockConfluentSchemaRegistryClient.RegistryItem

class MockConfluentSchemaRegistryClient(data: List[RegistryItem]) extends ConfluentSchemaRegistryClient with LazyLogging {

  lazy val client: MockSchemaRegistryClient = {
    logger.debug(s"Create MockSchemaRegistryClient with data: $data.")
    val client = new MockSchemaRegistryClient
    data.foreach(item => register(client, item))
    client
  }

  override def getLatestSchema(subject: String): Validated[SchemaRegistryError, Schema] =
    handleClientError {
      validate(subject, Option.empty)
      AvroUtils.parseSchema(client.getLatestSchemaMetadata(subject).getSchema)
    }

  override def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryError, Schema] =
    handleClientError {
      validate(subject, Some(version))
      AvroUtils.parseSchema(client.getSchemaMetadata(subject, version).getSchema)
    }

  /**
    * MockSchemaRegistryClient doesn't throw right exception if subject or version doesn't exist
    */
  private def validate(subject: String, version: Option[Int]): Unit = {
    if (!client.getAllSubjects.contains(subject)) {
      throw new RestClientException("Subject not found", 404, ConfluentSchemaRegistryClient.subjectNotFoundCode)
    }

    if (!version.forall(client.getAllVersions(subject).contains(_))) {
      throw new RestClientException("Version not found", 404, ConfluentSchemaRegistryClient.versionNotFoundCode)
    }
  }

  private def register(mockSchemaRegistry: MockSchemaRegistryClient, item: RegistryItem): MockSchemaRegistryClient = {
    val subject = item.topic + "-" + (if (item.isKey) "key" else "value")
    mockSchemaRegistry.register(subject, item.schema, item.version, item.id)
    mockSchemaRegistry
  }
}

object MockConfluentSchemaRegistryClient {

  private[client] case class RegistryItem(topic: String, schema: Schema, version: Int, isKey: Boolean, id: Int)

  private[client] object RegistryItem {
    //Default value for autoincrement mock id
    private val AutoIncId = -1

    def apply(topic: String, schema: String, version: Int, isKey: Boolean): RegistryItem =
      new RegistryItem(topic, AvroUtils.parseSchema(schema), version, isKey, AutoIncId)

    def apply(topic: String, schema: Schema, version: Int, isKey: Boolean): RegistryItem =
      new RegistryItem(topic, schema, version, isKey, AutoIncId)
  }
}
