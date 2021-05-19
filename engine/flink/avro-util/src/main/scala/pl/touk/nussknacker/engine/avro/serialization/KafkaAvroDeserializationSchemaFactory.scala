package pl.touk.nussknacker.engine.avro.serialization

import java.nio.charset.StandardCharsets

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.reflect._

/**
  * Factory class for Flink's KeyedDeserializationSchema. It is extracted for purpose when for creation
  * of KeyedDeserializationSchema are needed additional avro related information.
  */
trait KafkaAvroDeserializationSchemaFactory extends Serializable {

  /**
    * Prepare Flink's KafkaDeserializationSchema based on provided information.
    *
    * @param kafkaConfig        Configuration of integration with Kafka.
    * @param keySchemaDataOpt   Schema which will be used as a key reader schema.
    * @param valueSchemaDataOpt Schema which will be used as a value reader schema. In case of None, writer schema will be used.
    * @tparam K Type that should be produced by key deserialization schema.
    * @tparam V Type that should be produced by value deserialization schema. It is important parameter, because factory can
    *           use other deserialization strategy base on it or provide different TypeInformation
    * @return KafkaDeserializationSchema
    */
  def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig,
                                       keySchemaDataOpt: Option[RuntimeSchemaData],
                                       valueSchemaDataOpt: Option[RuntimeSchemaData]
                                      ): KafkaDeserializationSchema[Any]

}

/**
  * Abstract base implementation of [[KafkaAvroDeserializationSchemaFactory]]
  * which uses Kafka's Deserializer in returned Flink's KeyedDeserializationSchema. It deserializes only value.
  */
abstract class KafkaAvroValueDeserializationSchemaFactory
  extends KafkaAvroDeserializationSchemaFactory {

  protected def createValueDeserializer[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[T]

  protected def createValueTypeInfo[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[T]

  override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig,
                                                keySchemaDataOpt: Option[RuntimeSchemaData],
                                                valueSchemaDataOpt: Option[RuntimeSchemaData]
                                               ): KafkaDeserializationSchema[Any] = {
    new KafkaDeserializationSchema[V] {
      @transient
      private lazy val deserializer = createValueDeserializer[V](valueSchemaDataOpt, kafkaConfig)

      override def deserialize(consumerRecord: ConsumerRecord[Array[Byte], Array[Byte]]): V = {
        val value = deserializer.deserialize(consumerRecord.topic(), consumerRecord.headers(), consumerRecord.value())
        value
      }

      override def isEndOfStream(nextElement: V): Boolean = false

      override def getProducedType: TypeInformation[V] = createValueTypeInfo(valueSchemaDataOpt, kafkaConfig)
    }
      .asInstanceOf[KafkaDeserializationSchema[Any]]
  }

}

/**
  * Abstract base implementation of [[KafkaAvroDeserializationSchemaFactory]]
  * which uses Kafka's Deserializer in returned Flink's KeyedDeserializationSchema. It deserializes both key and value
  * and wrap it in object
  */
abstract class KafkaAvroKeyValueDeserializationSchemaFactory
  extends KafkaAvroDeserializationSchemaFactory {

  protected type O

  protected def createKeyDeserializer[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[K]

  protected def createKeyTypeInfo[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[K]

  protected def createValueDeserializer[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[V]

  protected def createValueTypeInfo[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[V]

  protected def createObject[K: ClassTag, V: ClassTag](key: K, value: V, topic: String): O

  protected def createObjectTypeInformation[K: ClassTag, V: ClassTag](keyTypeInformation: TypeInformation[K], valueTypeInformation: TypeInformation[V]): TypeInformation[O]

  override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig,
                                                keySchemaDataOpt: Option[RuntimeSchemaData],
                                                valueSchemaDataOpt: Option[RuntimeSchemaData]
                                               ): KafkaDeserializationSchema[Any] = {

    new KafkaDeserializationSchema[O] {
      @transient
      private lazy val keyDeserializer = createKeyDeserializer[K](keySchemaDataOpt, kafkaConfig)
      @transient
      private lazy val valueDeserializer = createValueDeserializer[V](valueSchemaDataOpt, kafkaConfig)

      override def deserialize(consumerRecord: ConsumerRecord[Array[Byte], Array[Byte]]): O = {
        val key = keyDeserializer.deserialize(consumerRecord.topic(), consumerRecord.key())
        val value = valueDeserializer.deserialize(consumerRecord.topic(), consumerRecord.value())
        val obj = createObject[K, V](key, value, consumerRecord.topic())
        obj.asInstanceOf[O]
      }

      override def isEndOfStream(nextElement: O): Boolean = false

      override def getProducedType: TypeInformation[O] =
        createObjectTypeInformation[K, V](
          createKeyTypeInfo[K](keySchemaDataOpt, kafkaConfig),
          createValueTypeInfo[V](valueSchemaDataOpt, kafkaConfig)
        )
    }
      .asInstanceOf[KafkaDeserializationSchema[Any]]
  }

}
