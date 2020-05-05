package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryError
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryClientFactory.TypedConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.collection.mutable.ListBuffer

class MockConfluentSchemaRegistryClientFactory(data: List[RegistryItem]) extends ConfluentSchemaRegistryClientFactory {
  def createSchemaRegistryClient(kafkaConfig: KafkaConfig): TypedConfluentSchemaRegistryClient = {
    val mockSchemaRegistryClient: MockSchemaRegistryClient with ConfluentSchemaRegistryClient =
      new MockSchemaRegistryClient with ConfluentSchemaRegistryClient {
      override def getLatestSchema(subject: String): Validated[SchemaRegistryError, Schema] =
        handleClientError {
          validate(subject, Option.empty)
          AvroUtils.parseSchema(getLatestSchemaMetadata(subject).getSchema)
        }

      override def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryError, Schema] =
        handleClientError {
          validate(subject, Some(version))
          AvroUtils.parseSchema(getSchemaMetadata(subject, version).getSchema)
        }

      /**
        * MockSchemaRegistryClient doesn't throw right exception if subject or version doesn't exist
        */
      private def validate(subject: String, version: Option[Int]): Unit = {
        if (!getAllSubjects.contains(subject)) {
          throw new RestClientException("Subject not found", 404, subjectNotFoundCode)
        }

        if (!version.forall(getAllVersions(subject).contains(_))) {
          throw new RestClientException("Version not found", 404, versionNotFoundCode)
        }
      }
    }

    data.foreach(item => register(mockSchemaRegistryClient, item))
    mockSchemaRegistryClient
  }

  private def register(mockSchemaRegistry: MockSchemaRegistryClient, item: RegistryItem): MockSchemaRegistryClient = {
    val subject = item.topic + "-" + (if (item.isKey) "key" else "value")
    mockSchemaRegistry.register(subject, item.schema, item.version, item.id)
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
    new RegistryItem(topic, AvroUtils.parseSchema(schema), version, isKey, -1)

  def apply(topic: String, schema: Schema, version: Int, isKey: Boolean): RegistryItem =
    new RegistryItem(topic, schema, version, isKey, -1)
}
