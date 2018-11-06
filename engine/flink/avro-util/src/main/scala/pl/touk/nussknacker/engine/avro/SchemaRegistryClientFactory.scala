package pl.touk.nussknacker.engine.avro

import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaRegistryClient}
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import pl.touk.nussknacker.engine.kafka.KafkaConfig

class SchemaRegistryClientFactory extends Serializable {

  import collection.JavaConverters._

  // copied from AbstractKafkaAvroSerDe.configureClientProperties
  def createSchemaRegistryClient(kafkaConfig: KafkaConfig): SchemaRegistryClient = {
    val config = new KafkaAvroDeserializerConfig(kafkaConfig.kafkaProperties.getOrElse(Map.empty).asJava)
    val urls = config.getSchemaRegistryUrls
    val maxSchemaObject = config.getMaxSchemasPerSubject
    val originals = config.originalsWithPrefix("")
    new CachedSchemaRegistryClient(urls, maxSchemaObject, originals)
  }

}