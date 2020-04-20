package pl.touk.nussknacker.engine.avro

import io.confluent.kafka.serializers._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaDeserializationSchemaFactoryBase, KafkaKeyValueDeserializationSchemaFactoryBase}

trait AvroDeserializer {
  import collection.JavaConverters._

  protected def createDeserializer(schemaRegistryClientFactory: SchemaRegistryClientFactory, kafkaConfig: KafkaConfig, isKey: Boolean, useSpecificAvroReader: Boolean) = {
    // TODO: this client is never destroyed and it is potential leak of resources
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val deserializer = new KafkaAvroDeserializer(schemaRegistryClient)
    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty) + (
      KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG -> useSpecificAvroReader
    )
    deserializer.configure(props.asJava, isKey)
    deserializer
  }
}

class AvroDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClientFactory: SchemaRegistryClientFactory, useSpecificAvroReader: Boolean)
  extends KafkaDeserializationSchemaFactoryBase[T] with AvroDeserializer {

  override protected def createValueDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[T] =
    createDeserializer(schemaRegistryClientFactory, kafkaConfig, isKey = false, useSpecificAvroReader).asInstanceOf[Deserializer[T]]
}

abstract class AvroKeyValueDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClientFactory: SchemaRegistryClientFactory, useSpecificAvroReader: Boolean)
  extends KafkaKeyValueDeserializationSchemaFactoryBase[T] with AvroDeserializer {

  override protected def createKeyDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer(schemaRegistryClientFactory, kafkaConfig, isKey = true, useSpecificAvroReader).asInstanceOf[Deserializer[K]]

  override protected def createValueDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer(schemaRegistryClientFactory, kafkaConfig, isKey = false, useSpecificAvroReader).asInstanceOf[Deserializer[V]]
}
