package pl.touk.nussknacker.engine.avro.serialization

import java.lang

import org.apache.avro.Schema
import org.apache.flink.formats.avro.typeutils.NkSerializableAvroSchema
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaProducerHelper, UUIDSerializer}

/**
  * Factory class for Flink's KeyedSerializationSchema. It is extracted for purpose when for creation
  * of KeyedSerializationSchema are needed additional avro related information.
  */
trait KafkaAvroSerializationSchemaFactory extends Serializable {

  def create(topic: String, version: Option[Int], schemaOpt: Option[NkSerializableAvroSchema], kafkaConfig: KafkaConfig): KafkaSerializationSchema[AnyRef]

}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KeyedSerializationSchema. It serializes only value - key will be
  * randomly generated as a UUID.
  */
abstract class KafkaAvroValueSerializationSchemaFactory extends KafkaAvroSerializationSchemaFactory {

  protected def createKeySerializer(kafkaConfig: KafkaConfig): Serializer[AnyRef] =
    new UUIDSerializer[AnyRef]

  protected def createValueSerializer(schemaOpt: Option[Schema], version: Option[Int], kafkaConfig: KafkaConfig): Serializer[AnyRef]

  override def create(topic: String, version: Option[Int], schemaOpt: Option[NkSerializableAvroSchema], kafkaConfig: KafkaConfig): KafkaSerializationSchema[AnyRef] = {
    new KafkaSerializationSchema[AnyRef] {
      private lazy val keySerializer = createKeySerializer(kafkaConfig)
      private lazy val valueSerializer = createValueSerializer(schemaOpt.map(_.getAvroSchema), version, kafkaConfig)

      override def serialize(element: AnyRef, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
        KafkaProducerHelper.createRecord(topic,
          keySerializer.serialize(topic, element),
          valueSerializer.serialize(topic, element),
          timestamp)
      }
    }
  }
}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KeyedSerializationSchema. It serializes both key and value.
  */
abstract class KafkaAvroKeyValueSerializationSchemaFactory extends KafkaAvroSerializationSchemaFactory {

  protected type K

  protected type V

  // TODO We currently not support schema evolution for keys
  protected def createKeySerializer(kafkaConfig: KafkaConfig): Serializer[K]

  protected def createValueSerializer(schemaOpt: Option[Schema], version: Option[Int], kafkaConfig: KafkaConfig): Serializer[V]

  protected def extractKey(obj: AnyRef): K

  protected def extractValue(obj: AnyRef): V

  override def create(topic: String, version: Option[Int], schemaOpt: Option[NkSerializableAvroSchema], kafkaConfig: KafkaConfig): KafkaSerializationSchema[AnyRef] = {
    new KafkaSerializationSchema[AnyRef] {
      private lazy val keySerializer = createKeySerializer(kafkaConfig)
      private lazy val valueSerializer = createValueSerializer(schemaOpt.map(_.getAvroSchema), version, kafkaConfig)

      override def serialize(element: AnyRef, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
        val key = keySerializer.serialize(topic, extractKey(element))
        val value = valueSerializer.serialize(topic, extractValue(element))
        KafkaProducerHelper.createRecord(topic, key, value, timestamp)
      }
    }
  }
}
