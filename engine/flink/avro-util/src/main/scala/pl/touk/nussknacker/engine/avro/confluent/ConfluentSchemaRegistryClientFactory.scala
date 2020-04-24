package pl.touk.nussknacker.engine.avro.confluent

import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaRegistryClient => ConfluentSchemaRegistryClient}
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.{AvroUtils, SchemaRegistryClient, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

object ConfluentSchemaRegistryClientFactory extends SchemaRegistryClientFactory[ConfluentSchemaRegistryClient] {

  import collection.JavaConverters._

  // copied from AbstractKafkaAvroSerDe.configureClientProperties
  def createSchemaRegistryClient(kafkaConfig: KafkaConfig): TypedSchemaRegistryClient = {
    val config = new KafkaAvroDeserializerConfig(kafkaConfig.kafkaProperties.getOrElse(Map.empty).asJava)
    val urls = config.getSchemaRegistryUrls
    val maxSchemaObject = config.getMaxSchemasPerSubject
    val originals = config.originalsWithPrefix("")

    // It's fat object which can not be serialized
    new CachedSchemaRegistryClient(urls, maxSchemaObject, originals) with SchemaRegistryClient {
      override def getLatestSchema(subject: String): Schema =
        AvroUtils.parse(getLatestSchemaMetadata(subject).getSchema)
    }
  }
}
