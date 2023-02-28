package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils

import scala.jdk.CollectionConverters._

trait ConfluentSchemaRegistryClient extends SchemaRegistryClient with LazyLogging {

  import ConfluentSchemaRegistryClient._

  def client: CSchemaRegistryClient

  protected def handleClientError[T](data: => T): Validated[SchemaRegistryError, T] =
    try {
      valid(data)
    } catch {
      case exc: RestClientException if exc.getErrorCode == subjectNotFoundCode =>
        invalid(SchemaTopicError("Schema subject doesn't exist."))
      case exc: RestClientException if exc.getErrorCode == versionNotFoundCode =>
        invalid(SchemaVersionError("Schema version doesn't exist."))
      case exc: RestClientException if exc.getErrorCode == schemaNotFoundCode =>
        invalid(SchemaError("Schema doesn't exist."))
      case exc: Throwable =>
        logger.error("Unknown error on fetching schema data.", exc)
        invalid(SchemaRegistryUnknownError("Unknown error on fetching schema data.", exc))
    }
}

class DefaultConfluentSchemaRegistryClient(override val client: CSchemaRegistryClient)
  extends ConfluentSchemaRegistryClient with SchemaRegistryClientWithRegistration {

  override def getLatestFreshSchema(topic: String, isKey: Boolean): Validated[SchemaRegistryError, SchemaWithMetadata] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      val schemaMetadata = client.getLatestSchemaMetadata(subject)
      ConfluentUtils.toSchemaWithMetadata(schemaMetadata)
    }

  override def getByTopicAndVersion(topic: String, version: Int, isKey: Boolean): Validated[SchemaRegistryError, SchemaWithMetadata] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      val schemaMetadata = client.getSchemaMetadata(subject, version)
      ConfluentUtils.toSchemaWithMetadata(schemaMetadata)
    }

  override def getAllTopics: Validated[SchemaRegistryError, List[String]] =
    handleClientError {
      client.getAllSubjects.asScala.toList.collect(ConfluentUtils.topicFromSubject)
    }

  override def getAllVersions(topic: String, isKey: Boolean): Validated[SchemaRegistryError, List[Integer]] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      client.getAllVersions(subject).asScala.toList
    }

  override def getSchemaById(id: SchemaId): SchemaWithMetadata = {
    val schema = client.getSchemaById(id.asInt)
    SchemaWithMetadata(schema, id)
  }

  override def registerSchema(topic: String, isKey: Boolean, schema: ParsedSchema): SchemaId = {
    val subject = ConfluentUtils.topicSubject(topic, isKey)
    SchemaId.fromInt(client.register(subject, schema))
  }

}

object ConfluentSchemaRegistryClient {
  //The most common codes from https://docs.confluent.io/current/schema-registry/develop/api.html
  val subjectNotFoundCode = 40401
  val versionNotFoundCode = 40402
  val schemaNotFoundCode = 40403
}
