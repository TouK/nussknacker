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
    * @param kafkaConfig Configuration of integration with Kafka.
    * @param valueSchemaDataOpt Schema which will be used as a value reader schema. In case of None, writer schema will be used.
    * @param keySchemaDataOpt Schema which will be used as a key reader schema.
    * @param valueClassTagOpt Value definition in case of deserialization to SpecificRecord
    * @param keyClassTagOpt Key definition in case of deserialization to SpecificRecord
    * @tparam T Type that should be produced by deserialization schema. It is important parameter, because factory can
    *           use other deserialization strategy base on it or provide different TypeInformation
    * @return KafkaDeserializationSchema
    */
  def create[T: ClassTag](kafkaConfig: KafkaConfig,
                          valueSchemaDataOpt: Option[RuntimeSchemaData],
                          keySchemaDataOpt: Option[RuntimeSchemaData] = None,
                          valueClassTagOpt: Option[ClassTag[_]] = None,
                          keyClassTagOpt: Option[ClassTag[_]] = None
                         ): KafkaDeserializationSchema[T]

}

object KafkaAvroKeyValueDeserializationSchemaFactory {

  val fallbackKeyAsStringDeserializer: Deserializer[String] = new Deserializer[String] {
    override def deserialize(topic: String, data: Array[Byte]): String = {
      Option(data).map(bytes => new String(bytes, StandardCharsets.UTF_8)).orNull
    }
  }

  val fallbackKeyAsStringTypeInformation: TypeInformation[String] = TypeInformation.of(classOf[String])
}

/**
  * Abstract base implementation of [[KafkaAvroDeserializationSchemaFactory]]
  * which uses Kafka's Deserializer in returned Flink's KeyedDeserializationSchema. It deserializes only value.
  */
abstract class KafkaAvroValueDeserializationSchemaFactory
  extends KafkaAvroDeserializationSchemaFactory {

  protected def createValueDeserializer[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[T]

  protected def createValueTypeInfo[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[T]

  override def create[T: ClassTag](kafkaConfig: KafkaConfig,
                                   valueSchemaDataOpt: Option[RuntimeSchemaData],
                                   keySchemaDataOpt: Option[RuntimeSchemaData] = None,
                                   valueClassTagOpt: Option[ClassTag[_]] = None,
                                   keyClassTagOpt: Option[ClassTag[_]] = None
                                  ): KafkaDeserializationSchema[T] = {
    new KafkaDeserializationSchema[T] {
      @transient
      private lazy val deserializer = createValueDeserializer[T](valueSchemaDataOpt, kafkaConfig)

      override def deserialize(consumerRecord: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
        val value = deserializer.deserialize(consumerRecord.topic(), consumerRecord.headers(), consumerRecord.value())
        value
      }

      override def isEndOfStream(nextElement: T): Boolean = false

      override def getProducedType: TypeInformation[T] = createValueTypeInfo(valueSchemaDataOpt, kafkaConfig)
    }
  }

}

/**
  * Abstract base implementation of [[KafkaAvroDeserializationSchemaFactory]]
  * which uses Kafka's Deserializer in returned Flink's KeyedDeserializationSchema. It deserializes both key and value
  * and wrap it in object
  */
abstract class KafkaAvroKeyValueDeserializationSchemaFactory
  extends KafkaAvroDeserializationSchemaFactory {

  protected type K

  protected type V

  protected type O

  protected def createKeyDeserializer(schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig, keyClassTagOpt: Option[ClassTag[_]]): Deserializer[K]

  protected def createKeyTypeInfo(schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig, keyClassTagOpt: Option[ClassTag[_]]): TypeInformation[K]

  protected def createValueDeserializer(schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig, valueClassTagOpt: Option[ClassTag[_]]): Deserializer[V]

  protected def createValueTypeInfo(schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig, valueClassTagOpt: Option[ClassTag[_]]): TypeInformation[V]

  protected def createObject(key: K, value: V, topic: String): O

  protected def createObjectTypeInformation(keyTypeInformation: TypeInformation[K], valueTypeInformation: TypeInformation[V]): TypeInformation[O]

  override def create[T: ClassTag](kafkaConfig: KafkaConfig,
                                   valueSchemaDataOpt: Option[RuntimeSchemaData],
                                   keySchemaDataOpt: Option[RuntimeSchemaData] = None,
                                   valueClassTagOpt: Option[ClassTag[_]] = None,
                                   keyClassTagOpt: Option[ClassTag[_]] = None
                                  ): KafkaDeserializationSchema[T] = {

    new KafkaDeserializationSchema[T] {
      @transient
      private lazy val keyDeserializer = createKeyDeserializer(keySchemaDataOpt, kafkaConfig, keyClassTagOpt)
      @transient
      private lazy val valueDeserializer = createValueDeserializer(valueSchemaDataOpt, kafkaConfig, valueClassTagOpt)

      override def deserialize(consumerRecord: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
        val key = keyDeserializer.deserialize(consumerRecord.topic(), consumerRecord.key())
        val value = valueDeserializer.deserialize(consumerRecord.topic(), consumerRecord.value())
        val obj = createObject(key, value, consumerRecord.topic())
        obj.asInstanceOf[T]
      }

      override def isEndOfStream(nextElement: T): Boolean = false

      override def getProducedType: TypeInformation[T] =
        createObjectTypeInformation(
          createKeyTypeInfo(keySchemaDataOpt, kafkaConfig, keyClassTagOpt),
          createValueTypeInfo(valueSchemaDataOpt, kafkaConfig, valueClassTagOpt)
        ).asInstanceOf[TypeInformation[T]]
    }
  }

}
