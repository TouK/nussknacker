package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import cats.data.Validated
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryError
import pl.touk.nussknacker.engine.kafka.KafkaConfig

private[confluent] class DefaultConfluentSchemaRegistryClient(val client: CSchemaRegistryClient) extends ConfluentSchemaRegistryClient {

  def getLatestSchema(subject: String): Validated[SchemaRegistryError, Schema] =
    handleClientError {
      AvroUtils.extractSchema(client.getLatestSchemaMetadata(subject))
    }

  override def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryError, Schema] =
    handleClientError {
      AvroUtils.extractSchema(client.getSchemaMetadata(subject, version))
    }
}

object DefaultConfluentSchemaRegistryClient extends ConfluentSchemaRegistryClientFactory {

  override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient = {
    val client = CachedSchemaRegistryClient(kafkaConfig)
    createSchemaRegistryClient(client)
  }

  def createSchemaRegistryClient(client: CSchemaRegistryClient): ConfluentSchemaRegistryClient =
    new DefaultConfluentSchemaRegistryClient(client)
}
