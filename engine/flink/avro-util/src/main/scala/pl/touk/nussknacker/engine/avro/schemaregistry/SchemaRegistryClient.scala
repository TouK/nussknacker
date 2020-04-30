package pl.touk.nussknacker.engine.avro.schemaregistry

import org.apache.avro.Schema
import pl.touk.nussknacker.engine.kafka.KafkaConfig

trait SchemaRegistryClient extends Serializable {
  def getBySubjectAndVersion(subject: String, version: Int): Schema

  def getLatestSchema(subject: String): Schema

  def getSchema(subject: String, version: Option[Int]): Schema =
    version
      .map(ver => getBySubjectAndVersion(subject, ver))
      .getOrElse(getLatestSchema(subject))
}

trait SchemaRegistryClientFactory extends Serializable {
  def createSchemaRegistryClient(kafkaConfig: KafkaConfig): SchemaRegistryClient
}
