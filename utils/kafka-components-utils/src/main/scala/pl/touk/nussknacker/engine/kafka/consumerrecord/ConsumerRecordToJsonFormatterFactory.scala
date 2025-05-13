package pl.touk.nussknacker.engine.kafka.consumerrecord

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.test.TestRecord
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, RecordFormatterFactory}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

/**
  * RecordFormatter used to encode and decode whole raw kafka event (ConsumerRecord) in json format.
  * @tparam K - event key type with provided Encoder/Decoder
  * @tparam V - event value type with provided Encoder/Decoder
  */
class ConsumerRecordToJsonFormatterFactory[K: Encoder: Decoder, V: Encoder: Decoder] extends RecordFormatterFactory {

  override def create[KK: ClassTag, VV: ClassTag](
      kafkaConfig: KafkaConfig,
      kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[KK, VV]]
  ): RecordFormatter = {
    new ConsumerRecordToJsonFormatter[K, V](
      kafkaSourceDeserializationSchema.asInstanceOf[KafkaDeserializationSchema[ConsumerRecord[K, V]]]
    )
  }

}

class ConsumerRecordToJsonFormatter[K: Encoder: Decoder, V: Encoder: Decoder](
    kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]
) extends RecordFormatter {

  import pl.touk.nussknacker.engine.api.CirceUtil._

  /**
    * Step 1: Deserialize raw kafka event to [K, V] domain.
    * Step 2: Use provided K,V Encoders to convert event's data to json with derived encoder.
    */
  override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): TestRecord = {
    val deserializedRecord = kafkaSourceDeserializationSchema.deserialize(record)
    val serializableRecord = SerializableConsumerRecord(deserializedRecord)
    val consumerRecordEncoder: Encoder[SerializableConsumerRecord[K, V]] = deriveConfiguredEncoder
    TestRecord(consumerRecordEncoder(serializableRecord))
  }

  /**
    * Step 1: Use provided K,V Decoders to deserialize raw json bytes to SerializableConsumerRecord[K, V] domain.
    * Step 2: Use provided K,V Encoders to create key-value-to-bytes interpreter.
    * Step 3: Use interpreter to create raw kafka ConsumerRecord
    */
  override def parseRecord(
      topic: TopicName.ForSource,
      testRecord: TestRecord
  ): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val consumerRecordDecoder: Decoder[SerializableConsumerRecord[K, V]] =
      deriveConfiguredDecoder[SerializableConsumerRecord[K, V]]
    val serializableConsumerRecord = CirceUtil.decodeJsonUnsafe(testRecord.json)(consumerRecordDecoder)
    def serializeKeyValue(keyOpt: Option[K], value: V): (Array[Byte], Array[Byte]) = {
      (keyOpt.map(serialize[K]).orNull, serialize[V](value))
    }
    serializableConsumerRecord.toKafkaConsumerRecord(topic, serializeKeyValue)
  }

  private def serialize[T: Encoder](data: T): Array[Byte] = {
    val json = Encoder[T].apply(data)
    json match {
      // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
      case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
      case other           => other.noSpaces.getBytes(StandardCharsets.UTF_8)
    }
  }

}
