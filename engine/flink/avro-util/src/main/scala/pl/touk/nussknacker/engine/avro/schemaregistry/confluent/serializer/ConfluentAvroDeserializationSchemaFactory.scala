package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serializer

import io.confluent.kafka.serializers._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaDeserializationSchemaFactoryBase, KafkaKeyValueDeserializationSchemaFactoryBase}

trait ConfluentStaticAvroDeserializerFactory {
  import collection.JavaConverters._

  protected def createDeserializer[T](topic: String,
                                      version: Option[Int],
                                      schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                      kafkaConfig: KafkaConfig,
                                      isKey: Boolean,
                                      useSpecificAvroReader: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val schema = schemaRegistryClient.getSchema(AvroUtils.topicSubject(topic, isKey = isKey), version)
      .valueOr(exc => throw new SerializationException(s"Error retrieving Avro schema for topic $topic.", exc))

    val deserializer = new ConfluentStaticKafkaAvroDeserializer[T](schema, schemaRegistryClient, isKey)

    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty) + (
      KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG -> useSpecificAvroReader
    )

    deserializer.configure(props.asJava, isKey)
    deserializer
  }
}

class ConfluentStaticAvroDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, useSpecificAvroReader: Boolean)
  extends KafkaDeserializationSchemaFactoryBase[T] with ConfluentStaticAvroDeserializerFactory {

  override protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): Deserializer[T] =
    createDeserializer[T](topics.head, version, schemaRegistryClientFactory, kafkaConfig, isKey = false, useSpecificAvroReader)
}

abstract class ConfluentStaticAvroKeyValueDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, useSpecificAvroReader: Boolean)
  extends KafkaKeyValueDeserializationSchemaFactoryBase[T] with ConfluentStaticAvroDeserializerFactory {

  override protected def createKeyDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](topics.head, version, schemaRegistryClientFactory, kafkaConfig, isKey = true, useSpecificAvroReader)

  override protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](topics.head, version, schemaRegistryClientFactory, kafkaConfig, isKey = false, useSpecificAvroReader)
}
