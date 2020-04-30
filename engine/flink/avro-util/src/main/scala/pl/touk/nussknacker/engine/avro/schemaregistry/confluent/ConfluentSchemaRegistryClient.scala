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
    handleClientError {
      getLatestSchemaMetadata(subject)
    }

  override def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryClientError, Schema] =
    handleClientError {
      getSchemaMetadata(subject, version)
    }

  protected def handleClientError(schemaMetadata: => SchemaMetadata): Validated[SchemaRegistryClientError, Schema] =
    try {
      val schema = schemaMetadata.getSchema
      if (schema == null) {
        invalid(SchemaRegistryClientError("Topic schema doesn't exist."))
      } else {
        valid(AvroUtils.createSchema(schema))
      }
    } catch {
      case exc: RestClientException if exc.getErrorCode == subjectNotFoundCode =>
        invalid(SchemaRegistryClientError("Topic schema doesn't exist."))
      case exc: RestClientException if exc.getErrorCode == versionNotFoundCode =>
        invalid(SchemaRegistryClientError("Invalid topic schema version."))
      case exc: Throwable =>
        logger.error("Unknown error on fetching schema data.", exc)
        invalid(SchemaRegistryClientError("Unknown error on fetching schema data."))
    }
}
