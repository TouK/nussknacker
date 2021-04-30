package pl.touk.nussknacker.engine.kafka.consumerrecord

import java.nio.charset.StandardCharsets

import com.github.ghik.silencer.silent
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

import scala.annotation.nowarn

@silent("deprecated")
@nowarn("cat=deprecation")
class ConsumerRecordToJsonFormatter[K: Encoder:Decoder, V: Encoder:Decoder](deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                                                            serializationSchema: KafkaSerializationSchema[ConsumerRecord[K, V]])
  extends RecordFormatter {

  implicit val consumerRecordDecoder: Decoder[SerializableConsumerRecord[K, V]] = deriveDecoder
  implicit val consumerRecordEncoder: Encoder[SerializableConsumerRecord[K, V]] = deriveEncoder

  override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = {
    val deserializedRecord = deserializationSchema.deserialize(record)
    val serializableRecord = SerializableConsumerRecord(
      Option(deserializedRecord.key()),
      deserializedRecord.value(),
      Option(deserializedRecord.topic()),
      Option(deserializedRecord.partition()),
      Option(deserializedRecord.offset()),
      Option(deserializedRecord.timestamp()),
      Option(ConsumerRecordUtils.toMap(deserializedRecord.headers()).mapValues(s => Option(s)))
    )
    implicitly[Encoder[SerializableConsumerRecord[K, V]]].apply(serializableRecord).noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  override protected def parseRecord(topic: String, bytes: Array[Byte]): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val serializableRecord = CirceUtil.decodeJsonUnsafe[SerializableConsumerRecord[K, V]](bytes) // decode json in SerializableConsumerRecord[K, V] domain
    val serializableConsumerRecord = SerializableConsumerRecord.from(topic, serializableRecord) // update with defaults if fields are missing in json
    // Here serialization schema and ProducerRecord are used to transform key and value to proper Array[Byte].
    // Other properties are ignored by serializer and are based on values provided by decoded json (or default empty values).
    val producerRecord = serializationSchema.serialize(serializableConsumerRecord, serializableConsumerRecord.timestamp()) // serialize K and V to Array[Byte]
    createConsumerRecord(
      serializableConsumerRecord.topic,
      serializableConsumerRecord.partition,
      serializableConsumerRecord.offset,
      serializableConsumerRecord.timestamp,
      producerRecord.key(),
      producerRecord.value(),
      producerRecord.headers()
    )
  }

  override protected def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit

}


case class SerializableConsumerRecord[K, V](key: Option[K], value: V, topic: Option[String], partition: Option[Int], offset: Option[Long], timestamp: Option[Long], headers: Option[Map[String, Option[String]]])

object SerializableConsumerRecord {

  def createConsumerRecord[K, V](topic: String, partition: Int, offset: Long, timestamp: Long, key: K, value: V, headers: Headers): ConsumerRecord[K, V] = {
    new ConsumerRecord(topic, partition, offset, timestamp,
      TimestampType.NO_TIMESTAMP_TYPE, ConsumerRecord.NULL_CHECKSUM.longValue(),
      ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE,
      key, value, headers
    )
  }

  def from[K, V](topic: String, record: SerializableConsumerRecord[K, V]): ConsumerRecord[K, V] = {
    createConsumerRecord(
      record.topic.getOrElse(topic),
      record.partition.getOrElse(0),
      record.offset.getOrElse(0L),
      record.timestamp.getOrElse(ConsumerRecord.NO_TIMESTAMP),
      record.key.getOrElse(null.asInstanceOf[K]),
      record.value,
      ConsumerRecordUtils.toHeaders(record.headers.map(_.mapValues(_.orNull)).getOrElse(Map.empty))
    )
  }
}
