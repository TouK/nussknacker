package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry._

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
        invalid(SchemaRegistryUnknownError("Unknown error on fetching schema data."))
    }
}

object ConfluentSchemaRegistryClient {
  //The most common codes from https://docs.confluent.io/current/schema-registry/develop/api.html
  val subjectNotFoundCode = 40401
  val versionNotFoundCode = 40402
  val schemaNotFoundCode = 40403
}
