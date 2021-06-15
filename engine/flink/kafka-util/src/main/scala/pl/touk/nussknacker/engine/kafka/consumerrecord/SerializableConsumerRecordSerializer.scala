package pl.touk.nussknacker.engine.kafka.consumerrecord

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.kafka.ConsumerRecordUtils

import java.util.Optional

abstract class SerializableConsumerRecordSerializer[K, V] extends Serializable {
  def serialize(topic: String, record: SerializableConsumerRecord[K, V]): ConsumerRecord[Array[Byte], Array[Byte]] = {
    new ConsumerRecord(
      topic,
      record.partition.getOrElse(0),
      record.offset.getOrElse(0L),
      record.timestamp.getOrElse(ConsumerRecord.NO_TIMESTAMP),
      record.timestampType.map(TimestampType.forName).getOrElse(TimestampType.NO_TIMESTAMP_TYPE),
      ConsumerRecord.NULL_CHECKSUM.toLong,
      ConsumerRecord.NULL_SIZE,
      ConsumerRecord.NULL_SIZE,
      extractKey(topic, record),
      extractValue(topic, record),
      ConsumerRecordUtils.toHeaders(record.headers.map(_.mapValues(_.orNull)).getOrElse(Map.empty)),
      Optional.ofNullable(record.leaderEpoch.map(Integer.valueOf).orNull) //avoids covert null -> 0 conversion
    )
  }

  protected def extractKey(topic: String, record: SerializableConsumerRecord[K, V]): Array[Byte]

  protected def extractValue(topic: String, record: SerializableConsumerRecord[K, V]): Array[Byte]
}
