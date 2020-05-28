package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{BaseKafkaVersionAwareSerializationSchemaFactory, BaseKafkaVersionAwareKeyValueSerializationSchemaFactory}

trait ConfluentAvroSerializerFactory {
  import collection.JavaConverters._

  protected def createSerializer[T](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, kafkaConfig: KafkaConfig, isKey: Boolean): Serializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val serializer = new KafkaAvroSerializer(schemaRegistryClient.client)
    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty)
    serializer.configure(props.asJava, isKey)
    serializer.asInstanceOf[Serializer[T]]
  }
}

class ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends BaseKafkaVersionAwareSerializationSchemaFactory[Any] with ConfluentAvroSerializerFactory {

  override protected def createValueSerializer(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): Serializer[Any] =
    createSerializer[Any](schemaRegistryClientFactory, kafkaConfig, isKey = false)
}

abstract class ConfluentAvroKeyValueSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends BaseKafkaVersionAwareKeyValueSerializationSchemaFactory[Any] with ConfluentAvroSerializerFactory {

  override protected def createKeySerializer(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): Serializer[K] =
    createSerializer[K](schemaRegistryClientFactory, kafkaConfig, isKey = true)

  override protected def createValueSerializer(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): Serializer[V] =
    createSerializer[V](schemaRegistryClientFactory, kafkaConfig, isKey = false)
}
