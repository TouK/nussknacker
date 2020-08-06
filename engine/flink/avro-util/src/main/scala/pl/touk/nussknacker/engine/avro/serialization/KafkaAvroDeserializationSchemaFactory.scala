package pl.touk.nussknacker.engine.avro.serialization

import org.apache.avro.Schema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.reflect._

/**
  * Factory class for Flink's KeyedDeserializationSchema. It is extracted for purpose when for creation
  * of KeyedDeserializationSchema are needed additional avro related information.
  */
trait KafkaAvroDeserializationSchemaFactory extends Serializable {

  /**
   * Prepare Flink's KafkaDeserializationSchema based on provided information.
   * @param schemaOpt Schema to which will be used as a reader schema. In case of None, will be used the same schema as writer schema.
   * @param kafkaConfig Configuration of integration with Kafka
   * @tparam T Type that should be produced by deserialization schema. It is important parameter, because factory can
   *           use other deserialization strategy base on it or provide different TypeInformation
   * @return KafkaDeserializationSchema
   */
  def create[T: ClassTag](schemaOpt: Option[Schema], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T]

}

/**
  * Abstract base implementation of [[KafkaAvroDeserializationSchemaFactory]]
  * which uses Kafka's Deserializer in returned Flink's KeyedDeserializationSchema. It deserializes only value.
  */
abstract class KafkaAvroValueDeserializationSchemaFactory
  extends KafkaAvroDeserializationSchemaFactory {

  protected def createValueDeserializer[T: ClassTag](schemaOpt: Option[Schema], kafkaConfig: KafkaConfig): Deserializer[T]

  protected def createValueTypeInfo[T: ClassTag](schemaOpt: Option[Schema]): TypeInformation[T]

  override def create[T: ClassTag](schemaOpt: Option[Schema], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T] = {
    new KafkaDeserializationSchema[T] {
      @transient
      private lazy val deserializer = createValueDeserializer[T](schemaOpt, kafkaConfig)

      override def deserialize(consumerRecord: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
        val value = deserializer.deserialize(consumerRecord.topic(), consumerRecord.value())
        value
      }

      override def isEndOfStream(nextElement: T): Boolean = false

      override def getProducedType: TypeInformation[T] = createValueTypeInfo(schemaOpt)
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

  // TODO Make this provided in params so one deserialization schema factory will work for multiple deserialization schemas
  protected def keyClassTag: ClassTag[K]

  protected def valueClassTag: ClassTag[V]

  protected def objectClassTag: ClassTag[O]

  // TODO We currently not support schema evolution for keys
  protected def createKeyDeserializer(kafkaConfig: KafkaConfig): Deserializer[K]

  protected def createKeyTypeInfo(): TypeInformation[K]

  protected def createValueDeserializer(schemaOpt: Option[Schema], kafkaConfig: KafkaConfig): Deserializer[V]

  protected def createValueTypeInfo(schemaOpt: Option[Schema]): TypeInformation[V]

  protected def createObject(key: K, value: V, topic: String): O

  protected def createObjectTypeInformation(keyTypeInformation: TypeInformation[K], valueTypeInformation: TypeInformation[V]): TypeInformation[O]

  override def create[T: ClassTag](schemaOpt: Option[Schema], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T] = {
    if (!classTag[T].runtimeClass.isAssignableFrom(objectClassTag.runtimeClass)) {
      throw new IllegalArgumentException("Illegal input class: " + classTag[T].runtimeClass)
    }
    new KafkaDeserializationSchema[T] {
      @transient
      private lazy val keyDeserializer = createKeyDeserializer(kafkaConfig)
      @transient
      private lazy val valueDeserializer = createValueDeserializer(schemaOpt, kafkaConfig)

      override def deserialize(consumerRecord: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
        val key = keyDeserializer.deserialize(consumerRecord.topic(), consumerRecord.key())
        val value = valueDeserializer.deserialize(consumerRecord.topic(), consumerRecord.value())
        val obj = createObject(key, value, consumerRecord.topic())
        obj.asInstanceOf[T]
      }

      override def isEndOfStream(nextElement: T): Boolean = false

      override def getProducedType: TypeInformation[T] = createObjectTypeInformation(createKeyTypeInfo(), createValueTypeInfo(schemaOpt)).asInstanceOf[TypeInformation[T]]
    }
  }

}
