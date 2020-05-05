package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.kafka.KafkaConfig

trait SchemaRegistryClient extends Serializable {

  def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryError, Schema]

  def getLatestSchema(subject: String): Validated[SchemaRegistryError, Schema]

  def getSchema(subject: String, version: Option[Int]): Validated[SchemaRegistryError, Schema] =
    version
      .map(ver => getBySubjectAndVersion(subject, ver))
      .getOrElse(getLatestSchema(subject))
}

trait SchemaRegistryClientFactory extends Serializable {
  def createSchemaRegistryClient(kafkaConfig: KafkaConfig): SchemaRegistryClient
}
