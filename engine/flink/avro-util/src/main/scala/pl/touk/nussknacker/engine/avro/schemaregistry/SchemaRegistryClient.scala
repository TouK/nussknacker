package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.kafka.KafkaConfig

trait SchemaRegistryClient extends Serializable {
  def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryClientError, Schema]

  def getLatestSchema(subject: String): Validated[SchemaRegistryClientError, Schema]

  def getSchema(subject: String, version: Option[Int]): Validated[SchemaRegistryClientError, Schema] =
    version
      .map(ver => getBySubjectAndVersion(subject, ver))
      .getOrElse(getLatestSchema(subject))
}

case class SchemaRegistryClientError(message: String, description: Option[String])

trait SchemaRegistryClientFactory extends Serializable {
  def createSchemaRegistryClient(kafkaConfig: KafkaConfig): SchemaRegistryClient
}
