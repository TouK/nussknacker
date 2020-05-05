package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.schemaregistry._

trait ConfluentSchemaRegistryClient extends SchemaRegistryClient with CSchemaRegistryClient with LazyLogging {
  //Codes from https://docs.confluent.io/current/schema-registry/develop/api.html
  protected val subjectNotFoundCode = 40401
  protected val versionNotFoundCode = 40402

  protected def handleClientError(schema: => Schema): Validated[SchemaRegistryError, Schema] =
    try {
      valid(schema)
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
