package pl.touk.nussknacker.engine.avro

import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaKeyValueSerializationSchemaFactoryBase, KafkaSerializationSchemaFactoryBase}

class AvroSerializationSchemaFactory(schemaRegistryClientFactory: SchemaRegistryClientFactory) extends KafkaSerializationSchemaFactoryBase[Any]  {

  import collection.JavaConverters._

  override protected def createValueSerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[Any] = {
    // TODO: this client is never destroyed and it is potential leak of resources
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val serializer = new KafkaAvroSerializer(schemaRegistryClient)
    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty)
    serializer.configure(props.asJava, false)
    serializer.asInstanceOf[Serializer[Any]]
  }

}

abstract class AvroKeyValueSerializationSchemaFactory(schemaRegistryClientFactory: SchemaRegistryClientFactory)
  extends KafkaKeyValueSerializationSchemaFactoryBase[Any] {

  import collection.JavaConverters._

  override protected def createKeySerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[K] = {
    createSerializer(kafkaConfig, isKey = true).asInstanceOf[Serializer[K]]
  }

  override protected def createValueSerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[V] = {
    createSerializer(kafkaConfig, isKey = false).asInstanceOf[Serializer[V]]
  }

  private def createSerializer(kafkaConfig: KafkaConfig, isKey: Boolean) = {
    // TODO: this client is never destroyed and it is potential leak of resources
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val serializer = new KafkaAvroSerializer(schemaRegistryClient)
    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty)
    serializer.configure(props.asJava, isKey)
    serializer
  }

}