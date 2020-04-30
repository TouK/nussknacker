package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.client.{SchemaMetadata, SchemaRegistryClient => ConfluenticSchemaRegistryClient}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryClient, SchemaRegistryClientError}

trait ConfluentSchemaRegistryClient extends SchemaRegistryClient with ConfluenticSchemaRegistryClient with LazyLogging {
  //Codes from https://docs.confluent.io/current/schema-registry/develop/api.html
  private val subjectNotFoundCode = 40401
  private val versionNotFoundCode = 40402

  override def getLatestSchema(subject: String): Validated[SchemaRegistryClientError, Schema] =
    handleClientError(subject, None) {
      getLatestSchemaMetadata(subject)
    }

  override def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryClientError, Schema] =
    handleClientError(subject, Some(version)) {
      getSchemaMetadata(subject, version)
    }

  protected def handleClientError(subject: String, version: Option[Int])(schemaMetadata: => SchemaMetadata): Validated[SchemaRegistryClientError, Schema] =
    try {
      val schema = schemaMetadata.getSchema
      if (schema == null) {
        invalid(SchemaRegistryClientError(s"Schema for subject `$subject` and version `${versionToString(version)}` doesn't exists.", None))
      } else {
        valid(AvroUtils.createSchema(schema))
      }
    } catch {
      case exc: RestClientException if exc.getErrorCode == subjectNotFoundCode =>
        invalid(SchemaRegistryClientError(s"Subject `$subject` doesn't exists.", Some(exc.getMessage)))
      case exc: RestClientException if exc.getErrorCode == versionNotFoundCode =>
        invalid(SchemaRegistryClientError(s"Version `${versionToString(version)}` for subject `$subject` doesn't exists.", Some(exc.getMessage)))
      case exc: Throwable =>
        logger.warn("Unknown error with fetching schema", exc)
        invalid(SchemaRegistryClientError("Unknown error with fetching schema.", Some(exc.getMessage)))
    }

  private def versionToString(version: Option[Int]): String =
    version.map(_.toString).getOrElse("")
}
