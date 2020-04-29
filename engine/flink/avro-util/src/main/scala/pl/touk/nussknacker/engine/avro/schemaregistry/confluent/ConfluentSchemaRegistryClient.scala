package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => ConfluenticSchemaRegistryClient}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryClient

trait ConfluentSchemaRegistryClient extends SchemaRegistryClient with ConfluenticSchemaRegistryClient {
  override def getLatestSchema(subject: String): Schema =
    AvroUtils.createSchema(getLatestSchemaMetadata(subject).getSchema)

  override def getBySubjectAndVersion(subject: String, version: Int): Schema =
    AvroUtils.createSchema(getSchemaMetadata(subject, version).getSchema)
}
