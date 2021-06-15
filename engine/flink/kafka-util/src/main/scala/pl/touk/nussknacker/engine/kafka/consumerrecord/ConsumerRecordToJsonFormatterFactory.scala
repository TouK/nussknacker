package pl.touk.nussknacker.engine.kafka.consumerrecord

import io.circe.{Decoder, Encoder}
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, RecordFormatterFactory}

import java.nio.charset.StandardCharsets

class ConsumerRecordToJsonFormatterFactory[K: Encoder : Decoder, V: Encoder : Decoder] extends RecordFormatterFactory {

  override def create[KK, VV](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[KK, VV]]): RecordFormatter = {
    new ConsumerRecordToJsonFormatter[K, V](
      createFormatterDeserializer[KK, VV](kafkaConfig, kafkaSourceDeserializationSchema).asInstanceOf[KafkaDeserializationSchema[ConsumerRecord[K, V]]],
      createFormatterSerializer[KK, VV](kafkaConfig).asInstanceOf[SerializableConsumerRecordSerializer[K, V]]
    )
  }

  protected def createFormatterDeserializer[KK, VV](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[KK, VV]]):
  KafkaDeserializationSchema[ConsumerRecord[KK, VV]] = {
    kafkaSourceDeserializationSchema
  }

  protected def createFormatterSerializer[KK, VV](kafkaConfig: KafkaConfig): SerializableConsumerRecordSerializer[KK, VV] = {
    val serializer = new SerializableConsumerRecordSerializer[K, V] {
      override protected def extractKey(topic: String, record: SerializableConsumerRecord[K, V]): Array[Byte] = record.key.map(encode[K]).orNull
      override protected def extractValue(topic: String, record: SerializableConsumerRecord[K, V]): Array[Byte] = encode[V](record.value)
    }
    serializer.asInstanceOf[SerializableConsumerRecordSerializer[KK, VV]]
  }

  private def encode[T: Encoder](data: T): Array[Byte] = {
    val json = Encoder[T].apply(data)
    json match {
      // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
      case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
      case other => other.noSpaces.getBytes(StandardCharsets.UTF_8)
    }
  }

}
