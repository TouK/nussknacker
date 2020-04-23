package pl.touk.nussknacker.engine.avro

import io.confluent.kafka.schemaregistry.client.SchemaMetadata
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.kafka.KafkaConfig

trait SchemaRegistryClient extends Serializable {

  def getById(id: Int): Schema

  def getSchemaMetadata(subject: String, version: Int): SchemaMetadata

  def getLatestSchemaMetadata(name: String): SchemaMetadata

  def schemaMetadata(subject: String, version: Option[Int]): SchemaMetadata =
    version
      .map(ver => getSchemaMetadata(subject, ver))
      .getOrElse(getLatestSchemaMetadata(subject))
}

trait SchemaRegistryClientFactory[Client] extends Serializable {
  type TypedSchemaRegistryClient = SchemaRegistryClient with Client
  def createSchemaRegistryClient(kafkaConfig: KafkaConfig): TypedSchemaRegistryClient
}
