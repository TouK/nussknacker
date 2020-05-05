package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.client.{SchemaMetadata, SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.{InvalidSchema, InvalidSchemaVersion, SchemaRegistryClient, SchemaRegistryError, SchemaRegistryUnknownError}

trait ConfluentSchemaRegistryClient extends SchemaRegistryClient with CSchemaRegistryClient with LazyLogging {
  //Codes from https://docs.confluent.io/current/schema-registry/develop/api.html
  protected val subjectNotFoundCode = 40401
  protected val versionNotFoundCode = 40402

  override def getLatestSchema(subject: String): Validated[SchemaRegistryError, Schema] =
    handleClientError {
      getLatestSchemaMetadata(subject)
    }

  override def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryError, Schema] =
    handleClientError {
      getSchemaMetadata(subject, version)
    }

  protected def handleClientError(schemaMetadata: => SchemaMetadata): Validated[SchemaRegistryError, Schema] =
    try {
      valid(AvroUtils.parseSchema(schemaMetadata.getSchema))
    } catch {
      case exc: RestClientException if exc.getErrorCode == subjectNotFoundCode =>
        invalid(InvalidSchema("Topic schema doesn't exist."))
      case exc: RestClientException if exc.getErrorCode == versionNotFoundCode =>
        invalid(InvalidSchemaVersion("Invalid topic schema version."))
      case exc: Throwable =>
        logger.error("Unknown error on fetching schema data.", exc)
        invalid(SchemaRegistryUnknownError("Unknown error on fetching schema data."))
    }
}
