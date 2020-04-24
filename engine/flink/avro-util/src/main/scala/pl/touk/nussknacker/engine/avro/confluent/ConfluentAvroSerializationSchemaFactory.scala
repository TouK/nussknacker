package pl.touk.nussknacker.engine.avro.confluent

import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaKeyValueSerializationSchemaFactoryBase, KafkaSerializationSchemaFactoryBase}

trait ConfluentAvroSerializerFactory {
  import collection.JavaConverters._

  protected def createSerializer(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, kafkaConfig: KafkaConfig, isKey: Boolean): KafkaAvroSerializer = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val serializer = new KafkaAvroSerializer(schemaRegistryClient)
    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty)
    serializer.configure(props.asJava, isKey)
    serializer
  }
}

class ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSerializationSchemaFactoryBase[Any] with ConfluentAvroSerializerFactory {

  override protected def createValueSerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[Any] =
    createSerializer(schemaRegistryClientFactory, kafkaConfig, isKey = false).asInstanceOf[Serializer[Any]]
}

abstract class ConfluentAvroKeyValueSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaKeyValueSerializationSchemaFactoryBase[Any] with ConfluentAvroSerializerFactory {

  override protected def createKeySerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[K] =
    createSerializer(schemaRegistryClientFactory, kafkaConfig, isKey = true).asInstanceOf[Serializer[K]]

  override protected def createValueSerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[V] =
    createSerializer(schemaRegistryClientFactory, kafkaConfig, isKey = false).asInstanceOf[Serializer[V]]
}
