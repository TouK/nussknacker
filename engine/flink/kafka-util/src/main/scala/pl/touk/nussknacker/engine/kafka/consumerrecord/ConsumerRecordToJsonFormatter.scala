package pl.touk.nussknacker.engine.kafka.consumerrecord

import java.nio.charset.StandardCharsets
import java.util.Optional

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.kafka.{ConsumerRecordUtils, RecordFormatter}

/**
  * RecordFormatter used to encode and decode whole raw kafka event (ConsumerRecord) in json format.
  *
  * @param deserializationSchema - schema used to convert raw kafka event to serializable representation (see SerializableConsumerRecord)
  * @param serializeKeyValue - converts key and value from K-V domain to Array[Byte]
  * @tparam K - event key type
  * @tparam V - event value type
  */
class ConsumerRecordToJsonFormatter[K: Encoder:Decoder, V: Encoder:Decoder](deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                                                            serializeKeyValue: (Option[K], V) => (Array[Byte], Array[Byte]))
  extends RecordFormatter {

  protected val consumerRecordDecoder: Decoder[SerializableConsumerRecord[K, V]] = deriveDecoder
  protected val consumerRecordEncoder: Encoder[SerializableConsumerRecord[K, V]] = deriveEncoder

  override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = {
    val deserializedRecord = deserializationSchema.deserialize(record)
    val serializableRecord = SerializableConsumerRecord(
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
    consumerRecordEncoder(serializableRecord).noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  override protected def parseRecord(topic: String, bytes: Array[Byte]): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val serializableConsumerRecord = CirceUtil.decodeJsonUnsafe(bytes)(consumerRecordDecoder) // decode json in SerializableConsumerRecord[K, V] domain
    serializableConsumerRecord.toKafkaConsumerRecord(topic, serializeKeyValue) // update with defaults and convert to ConsumerRecord
  }

  override protected def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit

}

/**
  * Wrapper for ConsumerRecord fields used for test data serialization, eg. json serialization.
  * All fields apart from value are optional.
  */
case class SerializableConsumerRecord[K, V](key: Option[K],
                                            value: V,
                                            topic: Option[String],
                                            partition: Option[Int],
                                            offset: Option[Long],
                                            timestamp: Option[Long],
                                            timestampType: Option[String],
                                            headers: Option[Map[String, Option[String]]],
                                            leaderEpoch: Option[Int]) {

  /**
    * Converts SerializableConsumerRecord to ConsumerRecord, uses default values in case of missing attributes.
    */
  def toKafkaConsumerRecord(formatterTopic: String, serializeKeyValue: (Option[K], V) => (Array[Byte], Array[Byte]) ): ConsumerRecord[Array[Byte], Array[Byte]] = {
    // serialize Key and Value to Array[Byte]
    val (keyBytes, valueBytes) = serializeKeyValue(key, value)
    // use defaults and ignore checksum, serializedKeySize and serializedValueSize
    new ConsumerRecord(
      topic.getOrElse(formatterTopic),
      partition.getOrElse(0),
      offset.getOrElse(0L),
      timestamp.getOrElse(ConsumerRecord.NO_TIMESTAMP),
      timestampType.map(TimestampType.forName).getOrElse(TimestampType.NO_TIMESTAMP_TYPE),
      ConsumerRecord.NULL_CHECKSUM.toLong,
      ConsumerRecord.NULL_SIZE,
      ConsumerRecord.NULL_SIZE,
      keyBytes,
      valueBytes,
      ConsumerRecordUtils.toHeaders(headers.map(_.mapValues(_.orNull)).getOrElse(Map.empty)),
      Optional.ofNullable(leaderEpoch.map(Integer.valueOf).orNull) //avoids covert null -> 0 conversion
    )
  }
}
