package pl.touk.nussknacker.engine.kafka.util

import java.nio.charset.StandardCharsets

import io.circe.Encoder
import io.circe.generic.JsonCodec
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.scalatest.{Assertion, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.CirceUtil.decodeJsonUnsafe
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.serialization.schemas.BaseSimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.util.KafkaGenericNodeMixin._
import pl.touk.nussknacker.engine.kafka.{ConsumerRecordUtils, KafkaSpec}
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.reflect.ClassTag

trait KafkaGenericNodeMixin extends FunSuite with Matchers with KafkaSpec with PatientScalaFutures {

  val sampleValue = SampleValue("first", "last")
  val sampleKey = SampleKey("one", 2L)
  val sampleHeaders = Map("headerOne" -> "valueOfHeaderOne", "headerTwo" -> null)

  val constTimestamp: Long = 123L

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

  protected def checkResult[K, V](a: ConsumerRecord[K, V], b: ConsumerRecord[K, V]): Assertion = {
    // "a shouldEqual b" raises TestFailedException so here compare field by field
    a.topic() shouldEqual b.topic()
    a.partition() shouldEqual b.partition()
    a.offset() shouldEqual b.offset()
    a.timestamp() shouldEqual b.timestamp()
    a.timestampType() shouldEqual b.timestampType()
    // skipping checksum, deprecated and when event is read from topic it comes with calculated checksum
    a.serializedKeySize() shouldEqual b.serializedKeySize()
    a.serializedValueSize() shouldEqual b.serializedValueSize()
    a.key() shouldEqual b.key()
    a.value() shouldEqual b.value()
    a.headers() shouldEqual b.headers()
    a.leaderEpoch() shouldEqual b.leaderEpoch()
  }

}

object KafkaGenericNodeMixin {

  @JsonCodec case class SampleKey(partOne: String, partTwo: Long)
  @JsonCodec case class SampleValue(id: String, field: String)
  @JsonCodec case class ObjToSerialize(value: SampleValue, key: SampleKey, headers: Map[String, String])

  implicit val sampleKeyTypeInformation: TypeInformation[SampleKey] = TypeInformation.of(classOf[SampleKey])
  implicit val sampleValueTypeInformation: TypeInformation[SampleValue] = TypeInformation.of(classOf[SampleValue])
  implicit val stringTypeInformation: TypeInformation[String] = TypeInformation.of(classOf[String])

  val sampleKeyDeserializationSchema = new EspDeserializationSchema[SampleKey](bytes => decodeJsonUnsafe[SampleKey](bytes))
  val sampleValueDeserializationSchema = new EspDeserializationSchema[SampleValue](bytes => decodeJsonUnsafe[SampleValue](bytes))
  val stringDeserializationSchema = new EspDeserializationSchema[String](bytes => Option(bytes).map(b => new String(b, StandardCharsets.UTF_8)).orNull)

}
