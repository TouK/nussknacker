package pl.touk.nussknacker.engine.kafka.util

import io.circe.Encoder
import io.circe.generic.JsonCodec
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.scalatest.FunSuite
import pl.touk.nussknacker.engine.api.CirceUtil.decodeJsonUnsafe
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.serialization.schemas.BaseSimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.util.KafkaGenericNodeMixin._
import pl.touk.nussknacker.engine.kafka.{ConsumerRecordUtils, KafkaSpec}
import pl.touk.nussknacker.test.PatientScalaFutures

trait KafkaGenericNodeMixin extends FunSuite with KafkaSpec with PatientScalaFutures {

  protected def objToSerializeSerializationSchema(topic: String): KafkaSerializationSchema[Any] = new BaseSimpleSerializationSchema[ObjToSerialize](
    topic,
    obj => Option(obj.value).map(v => implicitly[Encoder[SampleValue]].apply(v).noSpaces).orNull,
    obj => Option(obj.key).map(k => implicitly[Encoder[SampleKey]].apply(k).noSpaces).orNull,
    obj => ConsumerRecordUtils.toHeaders(obj.headers)
  ).asInstanceOf[KafkaSerializationSchema[Any]]

  protected def createTopic(name: String, partitions: Int = 1): String = {
    kafkaClient.createTopic(name, partitions = partitions)
    name
  }

  protected def pushMessage(kafkaSerializer: KafkaSerializationSchema[Any], obj: AnyRef, topic: String, partition: Option[Int] = None, timestamp: Long = 0L): RecordMetadata = {
    val record: ProducerRecord[Array[Byte], Array[Byte]] = kafkaSerializer.serialize(obj, timestamp)
    kafkaClient.sendRawMessage(topic = record.topic(), key = record.key(), content = record.value(), partition = partition, timestamp = record.timestamp(), headers = record.headers()).futureValue
  }

}

object KafkaGenericNodeMixin {

  @JsonCodec case class SampleKey(partOne: String, partTwo: Long)
  @JsonCodec case class SampleValue(id: String, field: String)
  @JsonCodec case class ObjToSerialize(value: SampleValue, key: SampleKey, headers: Map[String, String])

  implicit val keyTypeInformation: TypeInformation[SampleKey] = TypeInformation.of(classOf[SampleKey])
  implicit val valueTypeInformation: TypeInformation[SampleValue] = TypeInformation.of(classOf[SampleValue])

  val keyDeserializationSchema = new EspDeserializationSchema[SampleKey](bytes => decodeJsonUnsafe[SampleKey](bytes))
  val valueDeserializationSchema = new EspDeserializationSchema[SampleValue](bytes => decodeJsonUnsafe[SampleValue](bytes))

}
