package pl.touk.nussknacker.engine.kafka.consumerrecord

import java.nio.charset.StandardCharsets
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, RecordFormatterFactory}

import scala.reflect.ClassTag

/**
  * RecordFormatter used to encode and decode whole raw kafka event (ConsumerRecord) in json format.
  *
  * @param deserializationSchema - schema used to convert raw kafka event to serializable representation (see SerializableConsumerRecord)
  * @param serializeKeyValue - converts key and value from K-V domain to Array[Byte]
  * @tparam K - event key type
  * @tparam V - event value type
  */
class ConsumerRecordToJsonFormatterFactory[K:Encoder:Decoder, V:Encoder:Decoder] extends RecordFormatterFactory {

  override def create[KK: ClassTag, VV: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[KK, VV]]): RecordFormatter = {
    new ConsumerRecordToJsonFormatter[K, V](kafkaSourceDeserializationSchema.asInstanceOf[KafkaDeserializationSchema[ConsumerRecord[K, V]]])
  }

}

class ConsumerRecordToJsonFormatter[K:Encoder:Decoder, V:Encoder:Decoder](kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]) extends RecordFormatter {

  /**
    * Step 1: Deserialize raw kafka event to [K, V] domain.
    * Step 2: Use provided K,V Encoders to convert event's data to json with derived encoder.
    */
  override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = {
    val deserializedRecord = kafkaSourceDeserializationSchema.deserialize(record)
    val serializableRecord = SerializableConsumerRecord(deserializedRecord)
    val consumerRecordEncoder: Encoder[SerializableConsumerRecord[K, V]] = deriveEncoder
    consumerRecordEncoder(serializableRecord).noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  /**
    * Step 1: Use provided K,V Decoders to deserialize raw json bytes to SerializableConsumerRecord[K, V] domain.
    * Step 2: Use provided K,V Encoders to create key-value-to-bytes interpreter.
    * Step 3: Use interpreter to create raw kafka ConsumerRecord
    */
  override protected def parseRecord(topic: String, bytes: Array[Byte]): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val consumerRecordDecoder: Decoder[SerializableConsumerRecord[K, V]] = deriveDecoder
    val serializableConsumerRecord = CirceUtil.decodeJsonUnsafe(bytes)(consumerRecordDecoder)
    def serializeKeyValue(keyOpt: Option[K], value: V): (Array[Byte], Array[Byte]) = {
      (keyOpt.map(serialize[K]).orNull, serialize[V](value))
    }
    serializableConsumerRecord.toKafkaConsumerRecord(topic, serializeKeyValue)
  }

  override protected def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit

  private def serialize[T: Encoder](data: T): Array[Byte] = {
    val json = Encoder[T].apply(data)
    json match {
      // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
      case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
      case other => other.noSpaces.getBytes(StandardCharsets.UTF_8)
    }
  }

}

object ConsumerRecordToJsonFormatterFactory{
  def apply[K:Encoder:Decoder, V:Encoder:Decoder] = new ConsumerRecordToJsonFormatterFactory[K, V]
}
