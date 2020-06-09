package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import cats.data.Validated
import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaParseError, SchemaRegistryError}

import scala.util.Try

class FixedConfluentSchemaRegistryClient(subject: String, avroSchemaString: String) extends ConfluentSchemaRegistryClient with LazyLogging {

  private lazy val schema = Try(AvroUtils.parseSchema(avroSchemaString))
    .getOrElse(throw SchemaParseError("Provided value is not valid avro schema format."))

  override def getLatestFreshSchema(subject: String): Validated[SchemaRegistryError, Schema] =
    Valid(schema)

  override def getLatestSchema(subject: String): Validated[SchemaRegistryError, Schema] =
    Valid(schema)

  override def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryError, Schema] =
    Valid(schema)

  override def client: SchemaRegistryClient = {
    val client = new MockSchemaRegistryClient
    client.register(subject, schema)
    client
  }
}
