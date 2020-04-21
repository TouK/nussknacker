package pl.touk.nussknacker.engine.avro.confluent

import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaRegistryClient => ConfluentKafkaSchemaRegistryClient}
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.SchemaRegistryClient
import pl.touk.nussknacker.engine.kafka.KafkaConfig

class ConfluentSchemaRegistryClient(var confluentClient: ConfluentKafkaSchemaRegistryClient) extends SchemaRegistryClient with Serializable {
  override def schemaById(id: Int): Schema =
    confluentClient.getById(id)

  override def schemaBySubjectAndVersion(subject: String, version: Int): String =
    confluentClient.getSchemaMetadata(subject, version).getSchema

  override def latestSchema(name: String): String =
    confluentClient.getLatestSchemaMetadata(name).getSchema
}

object ConfluentSchemaRegistryClient {
  import collection.JavaConverters._

  // copied from AbstractKafkaAvroSerDe.configureClientProperties
  def apply(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient = {
    val config = new KafkaAvroDeserializerConfig(kafkaConfig.kafkaProperties.getOrElse(Map.empty).asJava)
    val urls = config.getSchemaRegistryUrls
    val maxSchemaObject = config.getMaxSchemasPerSubject
    val originals = config.originalsWithPrefix("")

    new ConfluentSchemaRegistryClient(
      new CachedSchemaRegistryClient(urls, maxSchemaObject, originals),
    )
  }
}
