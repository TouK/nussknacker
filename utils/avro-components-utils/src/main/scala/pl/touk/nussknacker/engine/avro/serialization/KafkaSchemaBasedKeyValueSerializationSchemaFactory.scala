package pl.touk.nussknacker.engine.avro.serialization

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.serialization.{CharSequenceSerializer, KafkaProducerHelper}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, serialization}
import pl.touk.nussknacker.engine.util.KeyedValue

import java.lang

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KafkaSerializationSchema for value - key will be taken from
  * step before serialization
  */
abstract class KafkaSchemaBasedValueSerializationSchemaFactory extends KafkaSchemaBasedSerializationSchemaFactory {

  protected def createKeySerializer(kafkaConfig: KafkaConfig): Serializer[AnyRef] = new CharSequenceSerializer

  // TODO: ParsedSchema
  protected def createValueSerializer(schemaOpt: Option[Schema], version: Option[Int], kafkaConfig: KafkaConfig): Serializer[Any]

  override def create(topic: String, version: Option[Int], schemaOpt: Option[NkSerializableParsedSchema[AvroSchema]], kafkaConfig: KafkaConfig): serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] = {
    new serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] {
      private lazy val keySerializer = createKeySerializer(kafkaConfig)
      private lazy val valueSerializer = createValueSerializer(schemaOpt.map(_.getParsedSchema.rawSchema()), version, kafkaConfig)

      override def serialize(element: KeyedValue[AnyRef, AnyRef], timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
        KafkaProducerHelper.createRecord(topic,
          keySerializer.serialize(topic, new RecordHeaders(), element.key),
          valueSerializer.serialize(topic, new RecordHeaders(), element.value),
          timestamp)
      }
    }
  }

}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KafkaSerializationSchema for both key and value. It ignores key
  * extracted in the step before serialization.
  */
abstract class KafkaSchemaBasedKeyValueSerializationSchemaFactory extends KafkaSchemaBasedSerializationSchemaFactory {

  protected type K

  protected type V

  // TODO We currently not support schema evolution for keys
  protected def createKeySerializer(kafkaConfig: KafkaConfig): Serializer[K]

  protected def createValueSerializer(schemaOpt: Option[Schema], version: Option[Int], kafkaConfig: KafkaConfig): Serializer[V]

  protected def extractKey(obj: AnyRef): K

  protected def extractValue(obj: AnyRef): V

  override def create(topic: String, version: Option[Int], schemaOpt: Option[NkSerializableParsedSchema[AvroSchema]], kafkaConfig: KafkaConfig): serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] = {
    new serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] {
      private lazy val keySerializer = createKeySerializer(kafkaConfig)
      private lazy val valueSerializer = createValueSerializer(schemaOpt.map(_.getParsedSchema.rawSchema()), version, kafkaConfig)

      override def serialize(element: KeyedValue[AnyRef, AnyRef], timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
        val key = keySerializer.serialize(topic, new RecordHeaders(), extractKey(element.value))
        val value = valueSerializer.serialize(topic, new RecordHeaders(), extractValue(element.value))
        //TODO: can we e.g. serialize schemaId to headers?
        KafkaProducerHelper.createRecord(topic, key, value, timestamp)
      }
    }
  }
}
