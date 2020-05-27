package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import io.confluent.kafka.serializers._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{BaseKafkaDeserializationSchemaVersionAwareFactory, BaseKeyValueKafkaDeserializationSchemaVersionAwareFactory}

trait ConfluentKafkaAvroDeserializerFactory {
  import collection.JavaConverters._

  protected def createDeserializer[T](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, kafkaConfig: KafkaConfig, isKey: Boolean, useSpecificAvroReader: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val deserializer = new KafkaAvroDeserializer(schemaRegistryClient.client)
    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty) + (
      KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG -> useSpecificAvroReader
    )
    deserializer.configure(props.asJava, isKey)
    deserializer.asInstanceOf[Deserializer[T]]
  }
}

class ConfluentKafkaAvroDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, useSpecificAvroReader: Boolean)
  extends BaseKafkaDeserializationSchemaVersionAwareFactory[T] with ConfluentKafkaAvroDeserializerFactory {

  override protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): Deserializer[T] =
    createDeserializer[T](schemaRegistryClientFactory, kafkaConfig, isKey = false, useSpecificAvroReader)
}

abstract class ConfluentKeyValueKafkaAvroDeserializationFactory[T: TypeInformation](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, useSpecificAvroReader: Boolean)
  extends BaseKeyValueKafkaDeserializationSchemaVersionAwareFactory[T] with ConfluentKafkaAvroDeserializerFactory {

  override protected def createKeyDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](schemaRegistryClientFactory, kafkaConfig, isKey = true, useSpecificAvroReader)

  override protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](schemaRegistryClientFactory, kafkaConfig, isKey = false, useSpecificAvroReader)
}
