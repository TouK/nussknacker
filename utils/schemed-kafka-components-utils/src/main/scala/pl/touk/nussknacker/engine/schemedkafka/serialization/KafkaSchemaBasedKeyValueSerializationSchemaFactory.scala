package pl.touk.nussknacker.engine.schemedkafka.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.serialization.{CharSequenceSerializer, KafkaProducerHelper}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, serialization}
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.universal.ConfluentUniversalKafkaSerde.ValueSchemaIdHeaderName
import pl.touk.nussknacker.engine.util.KeyedValue

import java.lang
import java.nio.charset.StandardCharsets

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KafkaSerializationSchema for value - key will be taken from
  * step before serialization
  */
abstract class KafkaSchemaBasedValueSerializationSchemaFactory extends KafkaSchemaBasedSerializationSchemaFactory {

  protected def createKeySerializer(kafkaConfig: KafkaConfig): Serializer[AnyRef] = new CharSequenceSerializer

  protected def createValueSerializer(schemaOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Serializer[Any]

  override def create(topic: String, schemaOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] = {
    new serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] {
      private lazy val keySerializer = createKeySerializer(kafkaConfig)
      private lazy val valueSerializer = createValueSerializer(schemaOpt, kafkaConfig)

      override def serialize(element: KeyedValue[AnyRef, AnyRef], timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
        val key = keySerializer.serialize(topic, element.key)
        // we have to create headers for each record because it can be enriched (mutated) by valueSerializer
        val headers = new RecordHeaders()
        schemaOpt.flatMap(_.schemaIdOpt).foreach(id => headers.add(ValueSchemaIdHeaderName, id.toString.getBytes(StandardCharsets.UTF_8)))
        val value = valueSerializer.serialize(topic, headers, element.value)
        KafkaProducerHelper.createRecord(topic, key, value, timestamp, headers)
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

  protected def createValueSerializer(schemaOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Serializer[V]

  protected def extractKey(obj: AnyRef): K

  protected def extractValue(obj: AnyRef): V

  override def create(topic: String, schemaOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] = {
    new serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] {
      private lazy val keySerializer = createKeySerializer(kafkaConfig)
      private lazy val valueSerializer = createValueSerializer(schemaOpt, kafkaConfig)

      override def serialize(element: KeyedValue[AnyRef, AnyRef], timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
        val key = keySerializer.serialize(topic, extractKey(element.value))
        // we have to create headers for each record because it can be enriched (mutated) by valueSerializer
        val headers = new RecordHeaders()
        schemaOpt.flatMap(_.schemaIdOpt).foreach(id => headers.add(ValueSchemaIdHeaderName, id.toString.getBytes(StandardCharsets.UTF_8)))
        val value = valueSerializer.serialize(topic, headers, extractValue(element.value))
        KafkaProducerHelper.createRecord(topic, key, value, timestamp, headers)
      }
    }
  }
}
