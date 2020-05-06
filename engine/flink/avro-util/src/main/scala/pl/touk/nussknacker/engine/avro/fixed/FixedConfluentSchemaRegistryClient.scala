package pl.touk.nussknacker.engine.avro.fixed

import cats.data.Validated
import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryError
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClient, ConfluentSchemaRegistryClient}

class FixedConfluentSchemaRegistryClient(schemaString: String) extends ConfluentSchemaRegistryClient with LazyLogging {

  lazy val schema: Schema = CachedConfluentSchemaRegistryClient.getOrCreate(schemaString.hashCode.toString, {
    logger.debug(s"Cached schema for key: ${schemaString.hashCode.toString} and string: $schemaString.")
    schemaString
  })

  lazy val client: MockSchemaRegistryClient = {
    logger.debug(s"Create MockSchemaRegistryClient with config: $schemaString.")

    new MockSchemaRegistryClient {
      override def getById(id: Int): Schema =
        schema

      override def getBySubjectAndId(subject: String, id: Int): Schema =
        schema
    }
  }

  override def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryError, Schema] =
    Valid(schema)

  override def getLatestSchema(subject: String): Validated[SchemaRegistryError, Schema] =
    Valid(schema)
}
