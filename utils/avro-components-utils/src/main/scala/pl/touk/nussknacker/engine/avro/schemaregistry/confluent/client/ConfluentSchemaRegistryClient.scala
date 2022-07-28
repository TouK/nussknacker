package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry._
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig

import scala.collection.JavaConverters._

trait ConfluentSchemaRegistryClient extends SchemaRegistryClient with LazyLogging {

  import ConfluentSchemaRegistryClient._

  def client: CSchemaRegistryClient

  protected def handleClientError[T](data: => T): Validated[SchemaRegistryError, T] =
    try {
      valid(data)
    } catch {
      case exc: RestClientException if exc.getErrorCode == subjectNotFoundCode =>
        invalid(SchemaSubjectNotFound("Schema subject doesn't exist."))
      case exc: RestClientException if exc.getErrorCode == versionNotFoundCode =>
        invalid(SchemaVersionNotFound("Schema version doesn't exist."))
      case exc: RestClientException if exc.getErrorCode == schemaNotFoundCode =>
        invalid(SchemaNotFound("Schema doesn't exist."))
      case exc: Throwable =>
        logger.error("Unknown error on fetching schema data.", exc)
        invalid(SchemaRegistryUnknownError("Unknown error on fetching schema data.", exc))
    }
}

class DefaultConfluentSchemaRegistryClient(override val client: CSchemaRegistryClient, config: SchemaRegistryClientKafkaConfig) extends ConfluentSchemaRegistryClient {

  override def getLatestFreshSchema(topic: String, isKey: Boolean): Validated[SchemaRegistryError, SchemaWithMetadata] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      val schemaMetadata = client.getLatestSchemaMetadata(subject)
      withExtraSchemaTypes(SchemaWithMetadata(schemaMetadata))
    }

  override def getBySubjectAndVersion(topic: String, version: Int, isKey: Boolean): Validated[SchemaRegistryError, SchemaWithMetadata] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      val schemaMetadata = client.getSchemaMetadata(subject, version)
      withExtraSchemaTypes(SchemaWithMetadata(schemaMetadata))
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

  override def getSchemaById(id: Int): SchemaWithMetadata = {
    //todo schemaCache is subject-version keyed, how to get it by id
    val rawSchema = client.getSchemaById(id)
    withExtraSchemaTypes(id, rawSchema)
  }

  private def withExtraSchemaTypes(id: Int, rawSchema: ParsedSchema) = {
    (rawSchema, config.avroPlainTextSerialization) match {
      case (schema: AvroSchema, Some(true)) => SchemaWithMetadata(AvroSchemaWithJsonPayload(schema), id)
      case _ => SchemaWithMetadata(rawSchema, id)
    }
  }

  private def withExtraSchemaTypes(schemaMetadata: SchemaWithMetadata): SchemaWithMetadata = {
    withExtraSchemaTypes(schemaMetadata.id, schemaMetadata.schema)
  }
}

object ConfluentSchemaRegistryClient {
  //The most common codes from https://docs.confluent.io/current/schema-registry/develop/api.html
  val subjectNotFoundCode = 40401
  val versionNotFoundCode = 40402
  val schemaNotFoundCode = 40403
}
