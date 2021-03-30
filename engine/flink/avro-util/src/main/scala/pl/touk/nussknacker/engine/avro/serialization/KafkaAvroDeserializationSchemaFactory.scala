package pl.touk.nussknacker.engine.avro.serialization

import java.nio.charset.StandardCharsets

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.annotation.nowarn
import scala.reflect._

/**
  * Factory class for Flink's KeyedDeserializationSchema. It is extracted for purpose when for creation
  * of KeyedDeserializationSchema are needed additional avro related information.
  */
trait KafkaAvroDeserializationSchemaFactory extends Serializable {

  /**
   * Prepare Flink's KafkaDeserializationSchema based on provided information.
   * @param valueSchemaDataOpt Schema which will be used as a value reader schema. In case of None, writer schema will be used.
   * @param kafkaConfig Configuration of integration with Kafka.
   * @param keySchemaDataOpt Schema which will be used as a key reader schema.
   * @tparam T Type that should be produced by deserialization schema. It is important parameter, because factory can
   *           use other deserialization strategy base on it or provide different TypeInformation
   * @return KafkaDeserializationSchema
   */
  def create[T: ClassTag](valueSchemaDataOpt: Option[RuntimeSchemaData],
                          kafkaConfig: KafkaConfig,
                          keySchemaDataOpt: Option[RuntimeSchemaData] = None,
                          valueClassTagOpt: Option[ClassTag[_]] = None,
                          keyClassTagOpt: Option[ClassTag[_]] = None
                         ): KafkaDeserializationSchema[T]

}

object KafkaAvroKeyValueDeserializationSchemaFactory {

  val defaultKeyAsStringDeserializer: Deserializer[String] = new Deserializer[String] {
    override def deserialize(topic: String, data: Array[Byte]): String = {
      Option(data).map(bytes => new String(bytes, StandardCharsets.UTF_8)).orNull
    }
  }

  val defaultKeyAsStringTypeInformation: TypeInformation[String] = TypeInformation.of(classOf[String])
}

/**
  * Abstract base implementation of [[KafkaAvroDeserializationSchemaFactory]]
  * which uses Kafka's Deserializer in returned Flink's KeyedDeserializationSchema. It deserializes only value.
  */
abstract class KafkaAvroValueDeserializationSchemaFactory
  extends KafkaAvroDeserializationSchemaFactory {

  protected def createValueDeserializer[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[T]

  protected def createValueTypeInfo[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[T]

  override def create[T: ClassTag](valueSchemaDataOpt: Option[RuntimeSchemaData],
                                   kafkaConfig: KafkaConfig,
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

  protected def createObject(record: ConsumerRecord[K, V]): O

  protected def createObjectTypeInformation(keyTypeInformation: TypeInformation[K], valueTypeInformation: TypeInformation[V]): TypeInformation[O]

  override def create[T: ClassTag](valueSchemaDataOpt: Option[RuntimeSchemaData],
                                   kafkaConfig: KafkaConfig,
                                   keySchemaDataOpt: Option[RuntimeSchemaData] = None,
                                   valueClassTagOpt: Option[ClassTag[_]] = None,
                                   keyClassTagOpt: Option[ClassTag[_]] = None
                                  ): KafkaDeserializationSchema[T] = {

    new KafkaDeserializationSchema[T] {
      @transient
      private lazy val keyDeserializer = createKeyDeserializer(keySchemaDataOpt, kafkaConfig, keyClassTagOpt)
      @transient
      private lazy val valueDeserializer = createValueDeserializer(valueSchemaDataOpt, kafkaConfig, valueClassTagOpt)

      @silent("deprecated")
      @nowarn("deprecated")
      override def deserialize(consumerRecord: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
        val key = keyDeserializer.deserialize(consumerRecord.topic(), consumerRecord.key())
        val value = valueDeserializer.deserialize(consumerRecord.topic(), consumerRecord.value())
        val record = new ConsumerRecord[K, V](
          consumerRecord.topic(),
          consumerRecord.partition(),
          consumerRecord.offset(),
          consumerRecord.timestamp(),
          consumerRecord.timestampType(),
          consumerRecord.checksum(),
          consumerRecord.serializedKeySize(),
          consumerRecord.serializedValueSize(),
          key,
          value,
          consumerRecord.headers(),
          consumerRecord.leaderEpoch()
        )
        val obj = createObject(record)
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
