package pl.touk.nussknacker.engine.kafka.consumerrecord

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.KafkaRecordUtils

import java.util.Optional
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

/**
  * Wrapper for ConsumerRecord fields used for test data serialization, eg. json serialization.
  * All fields apart from value are optional.
  */
case class SerializableConsumerRecord[K, V](
    key: Option[K],
    value: V,
    topic: Option[String],
    partition: Option[Int],
    offset: Option[Long],
    timestamp: Option[Long],
    timestampType: Option[String],
    headers: Option[Map[String, Option[String]]],
    leaderEpoch: Option[Int]
) {

  /**
    * Converts SerializableConsumerRecord to ConsumerRecord, uses default values in case of missing attributes.
    */
  def toKafkaConsumerRecord(
      formatterTopic: TopicName.ForSource,
      serializeKeyValue: (Option[K], V) => (Array[Byte], Array[Byte])
  ): ConsumerRecord[Array[Byte], Array[Byte]] = {
    // serialize Key and Value to Array[Byte]
    val (keyBytes, valueBytes) = serializeKeyValue(key, value)
    // use defaults and ignore checksum, serializedKeySize and serializedValueSize

    new ConsumerRecord(
      topic.getOrElse(formatterTopic.name),
      partition.getOrElse(0),
      offset.getOrElse(0L),
      timestamp.getOrElse(ConsumerRecord.NO_TIMESTAMP),
      timestampType.map(TimestampType.forName).getOrElse(TimestampType.NO_TIMESTAMP_TYPE),
      ConsumerRecord.NULL_SIZE,
      ConsumerRecord.NULL_SIZE,
      keyBytes,
      valueBytes,
      KafkaRecordUtils.toHeaders(headers.map(_.mapValuesNow(_.orNull).toMap).getOrElse(Map.empty)),
      Optional.ofNullable(leaderEpoch.map(Integer.valueOf).orNull) // avoids covert null -> 0 conversion
    )
  }

}

object SerializableConsumerRecord {

  def apply[K, V](deserializedRecord: ConsumerRecord[K, V]): SerializableConsumerRecord[K, V] = {
    SerializableConsumerRecord(
      Option(deserializedRecord.key()),
      deserializedRecord.value(),
      Option(deserializedRecord.topic()),
      Option(deserializedRecord.partition()),
      Option(deserializedRecord.offset()),
      Option(deserializedRecord.timestamp()),
      Option(deserializedRecord.timestampType().name),
      Option(KafkaRecordUtils.toMap(deserializedRecord.headers()).mapValuesNow(s => Option(s))),
      Option(deserializedRecord.leaderEpoch().orElse(null)).map(_.intValue()) // avoids covert null -> 0 conversion
    )
  }

}
