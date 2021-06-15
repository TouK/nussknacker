package pl.touk.nussknacker.engine.kafka.consumerrecord

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.kafka.{ConsumerRecordUtils, RecordFormatter}

import java.nio.charset.StandardCharsets

/**
  * RecordFormatter used to encode and decode whole raw kafka event (ConsumerRecord) in json format.
  *
  * @param deserializationSchema - schema used to convert raw kafka event to serializable representation (see SerializableConsumerRecord)
  * @param keyValueSerializer - converts key and value from K-V domain to Array[Byte]
  * @tparam K - event key type
  * @tparam V - event value type
  */
class ConsumerRecordToJsonFormatter[K: Encoder:Decoder, V: Encoder:Decoder](deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                                                            keyValueSerializer: SerializableConsumerRecordSerializer[K, V])
  extends RecordFormatter {

  protected val consumerRecordDecoder: Decoder[SerializableConsumerRecord[K, V]] =
    deriveDecoder[BaseSerializableConsumerRecord[K, V]].asInstanceOf[Decoder[SerializableConsumerRecord[K, V]]]
  protected val consumerRecordEncoder: Encoder[SerializableConsumerRecord[K, V]] =
    deriveEncoder[BaseSerializableConsumerRecord[K, V]].asInstanceOf[Encoder[SerializableConsumerRecord[K, V]]]

  override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = {
    val serializableRecord = prepareSerializableRecord(record)
    consumerRecordEncoder(serializableRecord).noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  // decode raw Array[Byte] content to [K, V] domain and prepare serializable record with all key-value-header-meta attributes
  protected def prepareSerializableRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): SerializableConsumerRecord[K, V] = {
    val deserializedRecord = deserializationSchema.deserialize(record)
    BaseSerializableConsumerRecord(deserializedRecord)
  }

  override protected def parseRecord(topic: String, bytes: Array[Byte]): ConsumerRecord[Array[Byte], Array[Byte]] = {
    // decode json in SerializableConsumerRecord[K, V] domain
    val serializableConsumerRecord = CirceUtil.decodeJsonUnsafe(bytes)(consumerRecordDecoder)
    // update with defaults and convert to ConsumerRecord
    keyValueSerializer.serialize(topic, serializableConsumerRecord)
  }

  override protected def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit

}

case class BaseSerializableConsumerRecord[K, V](key: Option[K],
                                                value: V,
                                                topic: Option[String],
                                                partition: Option[Int],
                                                offset: Option[Long],
                                                timestamp: Option[Long],
                                                timestampType: Option[String],
                                                headers: Option[Map[String, Option[String]]],
                                                leaderEpoch: Option[Int]) extends SerializableConsumerRecord[K, V]

object BaseSerializableConsumerRecord {
  def apply[K, V](deserializedRecord: ConsumerRecord[K, V]): BaseSerializableConsumerRecord[K, V] = {
    new BaseSerializableConsumerRecord(
      Option(deserializedRecord.key()),
      deserializedRecord.value(),
      Option(deserializedRecord.topic()),
      Option(deserializedRecord.partition()),
      Option(deserializedRecord.offset()),
      Option(deserializedRecord.timestamp()),
      Option(deserializedRecord.timestampType().name),
      Option(ConsumerRecordUtils.toMap(deserializedRecord.headers()).mapValues(s => Option(s))),
      Option(deserializedRecord.leaderEpoch().orElse(null)).map(_.intValue()) //avoids covert null -> 0 conversion
    )
  }
}