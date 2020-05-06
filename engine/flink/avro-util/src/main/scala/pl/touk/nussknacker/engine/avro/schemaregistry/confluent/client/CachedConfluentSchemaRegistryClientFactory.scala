package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import pl.touk.nussknacker.engine.kafka.KafkaConfig

object CachedConfluentSchemaRegistryClientFactory extends ConfluentSchemaRegistryClientFactory {

  import collection.JavaConverters._

  override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient = {
    val config = new KafkaAvroDeserializerConfig(kafkaConfig.kafkaProperties.getOrElse(Map.empty).asJava)
    val urls = config.getSchemaRegistryUrls
    val maxSchemaObject = config.getMaxSchemasPerSubject
    val originals = config.originalsWithPrefix("")
    val client = new CachedSchemaRegistryClient(urls, maxSchemaObject, originals)
    new CachedConfluentSchemaRegistryClient(client)
  }
}
