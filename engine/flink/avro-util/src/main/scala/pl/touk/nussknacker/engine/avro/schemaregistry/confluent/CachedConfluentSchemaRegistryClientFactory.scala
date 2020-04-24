package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryClientFactory.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.kafka.KafkaConfig

object CachedConfluentSchemaRegistryClientFactory extends ConfluentSchemaRegistryClientFactory {

  import collection.JavaConverters._

  // copied from AbstractKafkaAvroSerDe.configureClientProperties
  override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient = {
    val config = new KafkaAvroDeserializerConfig(kafkaConfig.kafkaProperties.getOrElse(Map.empty).asJava)
    val urls = config.getSchemaRegistryUrls
    val maxSchemaObject = config.getMaxSchemasPerSubject
    val originals = config.originalsWithPrefix("")

    // It's fat object which can not be serialized
    new CachedSchemaRegistryClient(urls, maxSchemaObject, originals) with SchemaRegistryClient {
      override def getLatestSchema(subject: String): Schema =
        AvroUtils.createSchema(getLatestSchemaMetadata(subject).getSchema)
    }
  }

}
