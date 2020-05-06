package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.schemaregistry._
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}

trait ConfluentSchemaRegistryClient extends SchemaRegistryClient with LazyLogging {

  import ConfluentSchemaRegistryClient._

  def client: CSchemaRegistryClient

  protected def handleClientError(schema: => Schema): Validated[SchemaRegistryError, Schema] =
    try {
      valid(schema)
    } catch {
      case exc: RestClientException if exc.getErrorCode == ConfluentSchemaRegistryClient.subjectNotFoundCode =>
        invalid(SchemaSubjectNotFound("Schema subject doesn't exist."))
      case exc: RestClientException if exc.getErrorCode == ConfluentSchemaRegistryClient.versionNotFoundCode =>
        invalid(SchemaVersionFound("Schema version doesn't exist."))
      case exc: Throwable =>
        logger.error("Unknown error on fetching schema data.", exc)
        invalid(SchemaRegistryUnknownError("Unknown error on fetching schema data."))
    }
}

object ConfluentSchemaRegistryClient {
  //Codes from https://docs.confluent.io/current/schema-registry/develop/api.html
  val subjectNotFoundCode = 40401
  val versionNotFoundCode = 40402
}
