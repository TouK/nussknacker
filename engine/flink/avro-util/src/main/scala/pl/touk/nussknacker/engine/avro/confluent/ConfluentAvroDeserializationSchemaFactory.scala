package pl.touk.nussknacker.engine.avro.confluent

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => ConfluentKafkaSchemaRegistryClient}
import io.confluent.kafka.serializers._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaDeserializationSchemaFactoryBase, KafkaKeyValueDeserializationSchemaFactoryBase}

trait AvroDeserializer {
  import collection.JavaConverters._

  protected def createDeserializer(schemaRegistryClient: ConfluentKafkaSchemaRegistryClient, kafkaConfig: KafkaConfig, isKey: Boolean, useSpecificAvroReader: Boolean) = {
    val deserializer = new KafkaAvroDeserializer(schemaRegistryClient)
    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty) + (
      KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG -> useSpecificAvroReader
    )
    deserializer.configure(props.asJava, isKey)
    deserializer
  }
}

class ConfluentAvroDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClient: ConfluentKafkaSchemaRegistryClient, useSpecificAvroReader: Boolean)
  extends KafkaDeserializationSchemaFactoryBase[T] with AvroDeserializer {

  override protected def createValueDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[T] =
    createDeserializer(schemaRegistryClient, kafkaConfig, isKey = false, useSpecificAvroReader).asInstanceOf[Deserializer[T]]
}

abstract class ConfluentAvroKeyValueDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClient: ConfluentKafkaSchemaRegistryClient, useSpecificAvroReader: Boolean)
  extends KafkaKeyValueDeserializationSchemaFactoryBase[T] with AvroDeserializer {

  override protected def createKeyDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer(schemaRegistryClient, kafkaConfig, isKey = true, useSpecificAvroReader).asInstanceOf[Deserializer[K]]

  override protected def createValueDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer(schemaRegistryClient, kafkaConfig, isKey = false, useSpecificAvroReader).asInstanceOf[Deserializer[V]]
}
