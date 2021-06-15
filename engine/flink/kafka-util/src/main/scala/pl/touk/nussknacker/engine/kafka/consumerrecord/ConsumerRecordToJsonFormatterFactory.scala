package pl.touk.nussknacker.engine.kafka.consumerrecord

import io.circe.{Decoder, Encoder}
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, RecordFormatterFactory}

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

class ConsumerRecordToJsonFormatterFactory[Key: Encoder : Decoder, Value: Encoder : Decoder] extends RecordFormatterFactory {

  override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = {
    new ConsumerRecordToJsonFormatter[Key, Value](
      createFormatterDeserializer[K, V](kafkaConfig, kafkaSourceDeserializationSchema).asInstanceOf[KafkaDeserializationSchema[ConsumerRecord[Key, Value]]],
      createFormatterSerializer[K, V](kafkaConfig).asInstanceOf[SerializableConsumerRecordSerializer[Key, Value]]
    )
  }

  protected def createFormatterDeserializer[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]):
  KafkaDeserializationSchema[ConsumerRecord[K, V]] = {
    kafkaSourceDeserializationSchema
  }

  protected def createFormatterSerializer[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig): SerializableConsumerRecordSerializer[K, V] = {
    val serializer = new SerializableConsumerRecordSerializer[Key, Value] {
      override protected def extractKey(topic: String, record: SerializableConsumerRecord[Key, Value]): Array[Byte] = record.key.map(encode[Key]).orNull
      override protected def extractValue(topic: String, record: SerializableConsumerRecord[Key, Value]): Array[Byte] = encode[Value](record.value)
    }
    serializer.asInstanceOf[SerializableConsumerRecordSerializer[K, V]]
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
