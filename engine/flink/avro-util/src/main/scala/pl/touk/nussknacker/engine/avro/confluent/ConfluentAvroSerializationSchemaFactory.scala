package pl.touk.nussknacker.engine.avro.confluent

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => ConfluentKafkaSchemaRegistryClient}
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaKeyValueSerializationSchemaFactoryBase, KafkaSerializationSchemaFactoryBase}

trait AvroSerializer {
  import collection.JavaConverters._

  protected def createSerializer(schemaRegistryClient: ConfluentKafkaSchemaRegistryClient, kafkaConfig: KafkaConfig, isKey: Boolean) = {
    val serializer = new KafkaAvroSerializer(schemaRegistryClient)
    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty)
    serializer.configure(props.asJava, isKey)
    serializer
  }
}

class ConfluentAvroSerializationSchemaFactory(schemaRegistryClient: ConfluentKafkaSchemaRegistryClient)
  extends KafkaSerializationSchemaFactoryBase[Any] with AvroSerializer {

  override protected def createValueSerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[Any] =
    createSerializer(schemaRegistryClient, kafkaConfig, isKey = false).asInstanceOf[Serializer[Any]]
}

abstract class ConfluentAvroKeyValueSerializationSchemaFactory(schemaRegistryClient: ConfluentKafkaSchemaRegistryClient)
  extends KafkaKeyValueSerializationSchemaFactoryBase[Any] with AvroSerializer {

  override protected def createKeySerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[K] =
    createSerializer(schemaRegistryClient, kafkaConfig, isKey = true).asInstanceOf[Serializer[K]]

  override protected def createValueSerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[V] =
    createSerializer(schemaRegistryClient, kafkaConfig, isKey = false).asInstanceOf[Serializer[V]]
}
