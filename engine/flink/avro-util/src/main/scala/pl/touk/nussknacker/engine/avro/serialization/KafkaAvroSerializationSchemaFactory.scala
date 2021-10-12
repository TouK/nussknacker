package pl.touk.nussknacker.engine.avro.serialization

import java.lang
import org.apache.avro.Schema
import org.apache.flink.formats.avro.typeutils.NkSerializableAvroSchema
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.flink.util.keyed.KeyedValue
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, serialization}
import pl.touk.nussknacker.engine.kafka.serialization.{CharSequenceSerializer, KafkaProducerHelper}

/**
  * Factory class for Flink's KeyedSerializationSchema. It is extracted for purpose when for creation
  * of KafkaSerializationSchema are needed additional avro related information. SerializationSchema will take
 *  KafkaSerializationSchema with key extracted in the step before serialization
  */
trait KafkaAvroSerializationSchemaFactory extends Serializable {

  def create(topic: String, version: Option[Int], schemaOpt: Option[NkSerializableAvroSchema], kafkaConfig: KafkaConfig): serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]]

}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KafkaSerializationSchema for value - key will be taken from
  * step before serialization
  */
abstract class KafkaAvroValueSerializationSchemaFactory extends KafkaAvroSerializationSchemaFactory {

  protected def createKeySerializer(kafkaConfig: KafkaConfig): Serializer[AnyRef] = new CharSequenceSerializer

  protected def createValueSerializer(schemaOpt: Option[Schema], version: Option[Int], kafkaConfig: KafkaConfig): Serializer[Any]

  override def create(topic: String, version: Option[Int], schemaOpt: Option[NkSerializableAvroSchema], kafkaConfig: KafkaConfig): serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] = {
    new serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] {
      private lazy val keySerializer = createKeySerializer(kafkaConfig)
      private lazy val valueSerializer = createValueSerializer(schemaOpt.map(_.getAvroSchema), version, kafkaConfig)

      override def serialize(element: KeyedValue[AnyRef, AnyRef], timestamp: Long): ProducerRecord[Array[Byte], Array[Byte]] = {
        KafkaProducerHelper.createRecord(topic,
          keySerializer.serialize(topic, element.key),
          valueSerializer.serialize(topic, element.value),
          timestamp)
      }
    }
  }

}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KafkaSerializationSchema for both key and value. It ignores key
 *  extracted in the step before serialization.
  */
abstract class KafkaAvroKeyValueSerializationSchemaFactory extends KafkaAvroSerializationSchemaFactory {

  protected type K

  protected type V

  // TODO We currently not support schema evolution for keys
  protected def createKeySerializer(kafkaConfig: KafkaConfig): Serializer[K]

  protected def createValueSerializer(schemaOpt: Option[Schema], version: Option[Int], kafkaConfig: KafkaConfig): Serializer[V]

  protected def extractKey(obj: AnyRef): K

  protected def extractValue(obj: AnyRef): V

  override def create(topic: String, version: Option[Int], schemaOpt: Option[NkSerializableAvroSchema], kafkaConfig: KafkaConfig): serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] = {
    new serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] {
      private lazy val keySerializer = createKeySerializer(kafkaConfig)
      private lazy val valueSerializer = createValueSerializer(schemaOpt.map(_.getAvroSchema), version, kafkaConfig)

      override def serialize(element: KeyedValue[AnyRef, AnyRef], timestamp: Long): ProducerRecord[Array[Byte], Array[Byte]] = {
        val key = keySerializer.serialize(topic, extractKey(element.value))
        val value = valueSerializer.serialize(topic, extractValue(element.value))
        //TODO: can we e.g. serialize schemaId to headers?
        KafkaProducerHelper.createRecord(topic, key, value, timestamp)
      }
    }
  }
}
