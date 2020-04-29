package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => ConfluenticSchemaRegistryClient}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryClient, SchemaRegistryClientError}

trait ConfluentSchemaRegistryClient extends SchemaRegistryClient with ConfluenticSchemaRegistryClient with LazyLogging {
  //Codes from https://docs.confluent.io/current/schema-registry/develop/api.html
  private val subjectNotFoundCode = 40401
  private val versionNotFoundCode = 40402

  override def getLatestSchema(subject: String): Validated[SchemaRegistryClientError, Schema] =
    handleClientError(subject, None) {
      AvroUtils.createSchema(getLatestSchemaMetadata(subject).getSchema)
    }

  override def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryClientError, Schema] =
    handleClientError(subject, Some(version)) {
      AvroUtils.createSchema(getSchemaMetadata(subject, version).getSchema)
    }

  def handleClientError(subject: String, version: Option[Int])(createSchemaAction: => Schema): Validated[SchemaRegistryClientError, Schema] =
    try {
      valid(createSchemaAction)
    } catch {
      case exc: java.io.IOException =>
        invalid(SchemaRegistryClientError(s"Schema for subject `$subject` and version `${version.toString}` doesn't exists.", exc.getMessage))
      case exc: RestClientException if exc.getErrorCode == subjectNotFoundCode =>
        invalid(SchemaRegistryClientError(s"Subject `$subject` doesn't exists.", exc.getMessage))
      case exc: RestClientException if exc.getErrorCode == versionNotFoundCode =>
        invalid(SchemaRegistryClientError(s"Version `${version.map(_.toString).getOrElse("")}` for subject `$subject` doesn't exists.", exc.getMessage))
      case exc: Throwable =>
        logger.warn("Unknown error with fetching schema", exc)
        invalid(SchemaRegistryClientError("Unknown error with fetching schema.", exc.getMessage))
    }
}
