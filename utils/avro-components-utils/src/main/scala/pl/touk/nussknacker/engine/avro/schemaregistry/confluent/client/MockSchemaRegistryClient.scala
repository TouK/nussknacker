package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.client.{SchemaMetadata, MockSchemaRegistryClient => CMockSchemaRegistryClient}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils

import java.util

/**
  * Extended Confluent MockSchemaRegistryClient - base one throws wrong exceptions when version or subject doesn't exist
  */
class MockSchemaRegistryClient extends CMockSchemaRegistryClient with LazyLogging {

  import ConfluentSchemaRegistryClient._

  override def getSchemaMetadata(subject: String, version: Int): SchemaMetadata = {
    verify(subject, Some(version))
    super.getSchemaMetadata(subject, version)
  }

  override def getLatestSchemaMetadata(subject: String): SchemaMetadata = {
    verify(subject, None)
    super.getLatestSchemaMetadata(subject)
  }


  override def getAllVersions(subject: String): util.List[Integer] = {
    verify(subject, None)
    super.getAllVersions(subject)
  }

  /**
    * MockSchemaRegistryClient doesn't throw right exception if subject or version doesn't exist
    */
  private def verify(subject: String, version: Option[Int]): Unit = {
    if (!getAllSubjects.contains(subject)) {
      throw new RestClientException("Subject not found", 404, subjectNotFoundCode)
    }

    if (!version.forall(getAllVersions(subject).contains(_))) {
      throw new RestClientException("Version not found", 404, versionNotFoundCode)
    }
  }

  def registerKeySchema(topic: String, schema: Schema, version: Option[Int] = None): Int =
    register(topic, isKey = true, schema, version)

  def registerValueSchema(topic: String, schema: Schema, version: Option[Int] = None): Int =
    register(topic, isKey = false, schema, version)

  def register(topic: String, isKey: Boolean, schema: Schema, version: Option[Int]): Int = {
    register(
      ConfluentUtils.topicSubject(topic, isKey),
      ConfluentUtils.convertToAvroSchema(schema, version))
  }

}
