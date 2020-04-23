package pl.touk.nussknacker.engine.avro.confluent

import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaRegistryClient => ConfluentSchemaRegistryClient}
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import pl.touk.nussknacker.engine.avro.{SchemaRegistryClient, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

object ConfluentSchemaRegistryClientFactory extends SchemaRegistryClientFactory[ConfluentSchemaRegistryClient] {

  import collection.JavaConverters._

  // copied from AbstractKafkaAvroSerDe.configureClientProperties
  def createSchemaRegistryClient(kafkaConfig: KafkaConfig): TypedSchemaRegistryClient = {
    val config = new KafkaAvroDeserializerConfig(kafkaConfig.kafkaProperties.getOrElse(Map.empty).asJava)
    val urls = config.getSchemaRegistryUrls
    val maxSchemaObject = config.getMaxSchemasPerSubject
    val originals = config.originalsWithPrefix("")

    new CachedSchemaRegistryClient(urls, maxSchemaObject, originals) with SchemaRegistryClient
  }
}
