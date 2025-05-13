package pl.touk.nussknacker.engine.schemedkafka.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.{serialization, KafkaConfig}
import pl.touk.nussknacker.engine.kafka.serialization.{CharSequenceSerializer, KafkaProducerHelper}
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.schemaid.SchemaIdFromNuHeadersPotentiallyShiftingConfluentPayload.ValueSchemaIdHeaderName
import pl.touk.nussknacker.engine.util.KeyedValue

import java.lang
import java.nio.charset.StandardCharsets

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KafkaSerializationSchema for value - key will be taken from
  * step before serialization
  */
abstract class KafkaSchemaBasedValueSerializationSchemaFactory extends KafkaSchemaBasedSerializationSchemaFactory {

  protected[schemedkafka] def createKeySerializer(kafkaConfig: KafkaConfig): Serializer[AnyRef] =
    new CharSequenceSerializer

  protected[schemedkafka] def createValueSerializer(
      schemaOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Serializer[Any]

  override def create(
      topic: TopicName.ForSink,
      schemaOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] = {
    new serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] {
      private lazy val keySerializer   = createKeySerializer(kafkaConfig)
      private lazy val valueSerializer = createValueSerializer(schemaOpt, kafkaConfig)

      override def serialize(
          element: KeyedValue[AnyRef, AnyRef],
          timestamp: lang.Long
      ): ProducerRecord[Array[Byte], Array[Byte]] = {
        // we have to create headers for each record because it can be enriched (mutated) by serializers
        val headers = new RecordHeaders()
        val key     = keySerializer.serialize(topic.name, headers, element.key)
        schemaOpt
          .flatMap(_.schemaIdOpt)
          .foreach(id => headers.add(ValueSchemaIdHeaderName, id.toString.getBytes(StandardCharsets.UTF_8)))
        val value = valueSerializer.serialize(topic.name, headers, element.value)
        KafkaProducerHelper.createRecord(topic.name, key, value, timestamp, headers)
      }
    }
  }

}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KafkaSerializationSchema for both key and value.
  */
abstract class KafkaSchemaBasedKeyValueSerializationSchemaFactory extends KafkaSchemaBasedSerializationSchemaFactory {

  // TODO We currently not support schema evolution for keys
  protected[schemedkafka] def createKeySerializer(kafkaConfig: KafkaConfig): Serializer[Any]

  protected[schemedkafka] def createValueSerializer(
      schemaOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Serializer[Any]

  override def create(
      topic: TopicName.ForSink,
      schemaOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] = {
    new serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]] {
      private lazy val keySerializer   = createKeySerializer(kafkaConfig)
      private lazy val valueSerializer = createValueSerializer(schemaOpt, kafkaConfig)

      override def serialize(
          element: KeyedValue[AnyRef, AnyRef],
          timestamp: lang.Long
      ): ProducerRecord[Array[Byte], Array[Byte]] = {
        // we have to create headers for each record because it can be enriched (mutated) by serializers
        val headers = new RecordHeaders()
        val key     = keySerializer.serialize(topic.name, headers, element.key)
        schemaOpt
          .flatMap(_.schemaIdOpt)
          .foreach(id => headers.add(ValueSchemaIdHeaderName, id.toString.getBytes(StandardCharsets.UTF_8)))
        val value = valueSerializer.serialize(topic.name, headers, element.value)
        KafkaProducerHelper.createRecord(topic.name, key, value, timestamp, headers)
      }
    }
  }

}
