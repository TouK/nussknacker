package pl.touk.nussknacker.engine.kafka.consumerrecord

import java.nio.charset.StandardCharsets
import java.util.Optional

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.kafka.{ConsumerRecordUtils, RecordFormatter}
import pl.touk.nussknacker.engine.kafka.consumerrecord.SerializableConsumerRecord._

/**
  * RecordFormatter used to encode and decode whole raw kafka event (ConsumerRecord) in json format.
  *
  * @param deserializationSchema - schema used to convert raw kafka event to serializable representation (see SerializableConsumerRecord)
  * @param serializationSchema - schema used to convert serializable representation to raw kafka event
  * @tparam K - event key type
  * @tparam V - event value type
  */
class ConsumerRecordToJsonFormatter[K: Encoder:Decoder, V: Encoder:Decoder](deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                                                            serializationSchema: KafkaSerializationSchema[ConsumerRecord[K, V]])
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
    val serializableRecord = CirceUtil.decodeJsonUnsafe(bytes)(consumerRecordDecoder) // decode json in SerializableConsumerRecord[K, V] domain
    val serializableConsumerRecord = toConsumerRecord(topic, serializableRecord) // update with defaults if fields are missing in json
    // Here serialization schema and ProducerRecord are used to transform key and value to proper Array[Byte].
    // Other properties are ignored by serializer and are based on values provided by decoded json (or default empty values).
    val producerRecord = serializationSchema.serialize(serializableConsumerRecord, serializableConsumerRecord.timestamp()) // serialize K and V to Array[Byte]
    createConsumerRecord(
      serializableConsumerRecord.topic,
      serializableConsumerRecord.partition,
      serializableConsumerRecord.offset,
      serializableConsumerRecord.timestamp,
      serializableConsumerRecord.timestampType(),
      producerRecord.key(),
      producerRecord.value(),
      producerRecord.headers(),
      serializableConsumerRecord.leaderEpoch()
    )
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

}

object SerializableConsumerRecord {

  /**
    * Creates ConsumerRecord with default: checksum, serializedKeySize and serializedValueSize.
    */
  def createConsumerRecord[K, V](topic: String, partition: Int, offset: Long, timestamp: Long, timestampType: TimestampType, key: K, value: V, headers: Headers, leaderEpoch: Optional[Integer]): ConsumerRecord[K, V] = {
    new ConsumerRecord(topic, partition, offset,
      timestamp, timestampType,
      ConsumerRecord.NULL_CHECKSUM.longValue(), ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE,
      key, value, headers,
      leaderEpoch
    )
  }

  /**
    * Converts SerializableConsumerRecord to ConsumerRecord, uses default values in case of missing values.
    */
  def toConsumerRecord[K, V](topic: String, record: SerializableConsumerRecord[K, V]): ConsumerRecord[K, V] = {
    createConsumerRecord(
      record.topic.getOrElse(topic),
      record.partition.getOrElse(0),
      record.offset.getOrElse(0L),
      record.timestamp.getOrElse(ConsumerRecord.NO_TIMESTAMP),
      record.timestampType.map(TimestampType.forName).getOrElse(TimestampType.NO_TIMESTAMP_TYPE),
      record.key.getOrElse(null.asInstanceOf[K]),
      record.value,
      ConsumerRecordUtils.toHeaders(record.headers.map(_.mapValues(_.orNull)).getOrElse(Map.empty)),
      Optional.ofNullable(record.leaderEpoch.map(Integer.valueOf).orNull) //avoids covert null -> 0 conversion
    )
  }
}
