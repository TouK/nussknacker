package pl.touk.nussknacker.engine.avro.confluent

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => ConfluentSchemaRegistryClient}
import io.confluent.kafka.serializers._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaDeserializationSchemaFactoryBase, KafkaKeyValueDeserializationSchemaFactoryBase}

trait ConfluentAvroDeserializerFactory {
  import collection.JavaConverters._

  protected def createDeserializer(schemaRegistryClientFactory: SchemaRegistryClientFactory[ConfluentSchemaRegistryClient], kafkaConfig: KafkaConfig, isKey: Boolean, useSpecificAvroReader: Boolean): KafkaAvroDeserializer = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val deserializer = new KafkaAvroDeserializer(schemaRegistryClient)
    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty) + (
      KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG -> useSpecificAvroReader
    )
    deserializer.configure(props.asJava, isKey)
    deserializer
  }
}

class ConfluentAvroDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClientFactory: SchemaRegistryClientFactory[ConfluentSchemaRegistryClient], useSpecificAvroReader: Boolean)
  extends KafkaDeserializationSchemaFactoryBase[T] with ConfluentAvroDeserializerFactory {

  override protected def createValueDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[T] =
    createDeserializer(schemaRegistryClientFactory, kafkaConfig, isKey = false, useSpecificAvroReader).asInstanceOf[Deserializer[T]]
}

abstract class ConfluentAvroKeyValueDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClientFactory: SchemaRegistryClientFactory[ConfluentSchemaRegistryClient], useSpecificAvroReader: Boolean)
  extends KafkaKeyValueDeserializationSchemaFactoryBase[T] with ConfluentAvroDeserializerFactory {

  override protected def createKeyDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer(schemaRegistryClientFactory, kafkaConfig, isKey = true, useSpecificAvroReader).asInstanceOf[Deserializer[K]]

  override protected def createValueDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer(schemaRegistryClientFactory, kafkaConfig, isKey = false, useSpecificAvroReader).asInstanceOf[Deserializer[V]]
}
