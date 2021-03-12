package pl.touk.nussknacker.engine.kafka.consumerrecord

import java.nio.charset.{Charset, StandardCharsets}

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.kafka.{KafkaRecordHelper, RecordFormatter}

class ConsumerRecordToJsonFormatter[K:Encoder:Decoder, V:Encoder:Decoder](deserializationSchema: KafkaDeserializationSchema[DeserializedConsumerRecord[K,V]]) extends RecordFormatter {

  private val cs: Charset = StandardCharsets.UTF_8

  private val deserializer: KafkaDeserializationSchema[DeserializedConsumerRecord[K,V]] = deserializationSchema

  implicit val consumerRecordEncoder: Encoder[DeserializedConsumerRecord[K,V]] = deriveEncoder
  implicit val consumerRecordDecoder: Decoder[DeserializedConsumerRecord[K,V]] = deriveDecoder

  override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = {
    val consumerRecord = deserializer.deserialize(record)
    val bytes = implicitly[Encoder[DeserializedConsumerRecord[K,V]]].apply(consumerRecord).noSpaces.getBytes(cs)
    bytes
  }

  override protected def parseRecord(topic: String, bytes: Array[Byte]): ProducerRecord[Array[Byte], Array[Byte]] = {
    val consumerRecord = CirceUtil.decodeJsonUnsafe[DeserializedConsumerRecord[K,V]](bytes)
    val keyBytes = consumerRecord.key.map(k => implicitly[Encoder[K]].apply(k).noSpaces.getBytes(cs)).orNull
    val valueBytes = implicitly[Encoder[V]].apply(consumerRecord.value).noSpaces.getBytes(cs)
    new ProducerRecord[Array[Byte], Array[Byte]](
      consumerRecord.topic,
      consumerRecord.partition,
      consumerRecord.timestamp,
      keyBytes,
      valueBytes,
      KafkaRecordHelper.toHeaders(consumerRecord.headers)
    )
  }

  override protected def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit

}
