package pl.touk.nussknacker.engine.avro

import org.apache.avro.Schema
import pl.touk.nussknacker.engine.kafka.KafkaConfig

trait SchemaRegistryClient extends Serializable {

  def getById(id: Int): Schema

  def getBySubjectAndId(subject: String, version: Int): Schema

  def getLatestSchema(subject: String): Schema

  def getSchema(subject: String, version: Option[Int]): Schema =
    version
      .map(ver => getBySubjectAndId(subject, ver))
      .getOrElse(getLatestSchema(subject))

}

trait SchemaRegistryClientFactory extends Serializable {
  def createSchemaRegistryClient(kafkaConfig: KafkaConfig): SchemaRegistryClient
}
