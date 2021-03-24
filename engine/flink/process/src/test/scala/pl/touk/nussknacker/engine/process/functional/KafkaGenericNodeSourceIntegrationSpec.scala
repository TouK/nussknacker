package pl.touk.nussknacker.engine.process.functional

import io.circe.Encoder
import io.circe.generic.JsonCodec
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.kafka.{ConsumerRecordUtils, KafkaSpec}
import pl.touk.nussknacker.engine.kafka.serialization.schemas.BaseSimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaGenericNodeSourceFactory
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{SimpleJsonKey, SimpleJsonRecord, SinkForStrings}
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import pl.touk.nussknacker.engine.spel.Implicits._

class KafkaGenericNodeSourceIntegrationSpec extends FunSuite with Matchers with ProcessTestHelpers with KafkaSpec with VeryPatientScalaFutures {

  private val Topic: String = "genericNodeSource"

  @JsonCodec case class ObjToSerialize(value: SimpleJsonRecord, key: SimpleJsonKey, headers: Map[String, Option[String]])

  private lazy val ObjToSerializeSerializationSchema = new BaseSimpleSerializationSchema[ObjToSerialize](
    Topic,
    obj => implicitly[Encoder[SimpleJsonRecord]].apply(obj.value).noSpaces,
    obj => implicitly[Encoder[SimpleJsonKey]].apply(obj.key).noSpaces,
    obj => ConsumerRecordUtils.toHeaders(obj.headers)
  ).asInstanceOf[KafkaSerializationSchema[Any]]

  def pushMessage(kafkaSerializer: KafkaSerializationSchema[Any], obj: AnyRef, partition: Option[Int] = None, timestamp: Long = 0L): RecordMetadata = {
    val record: ProducerRecord[Array[Byte], Array[Byte]] = kafkaSerializer.serialize(obj, timestamp)
    kafkaClient.sendRawMessage(topic = record.topic(), key = record.key(), content = record.value(), partition = partition, timestamp = record.timestamp(), headers = record.headers()).futureValue
  }

  test("handle input variable with metadata provided by consumer record") {

    val givenObj = ObjToSerialize(
      SimpleJsonRecord("some id", "some field"),
      SimpleJsonKey("some key", 123L),
      Map("first" -> Some("header value"), "second" -> None)
    )
    pushMessage(ObjToSerializeSerializationSchema, givenObj, timestamp = 888L)

    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("procSource", "kafka-ConsumerRecord", KafkaGenericNodeSourceFactory.TopicParamName -> s"'${Topic}'")
        .buildSimpleVariable("a", "inputVariable", "#input.id + '|' + #input.field")
        .buildSimpleVariable("b", "metaVariable", "#inputMeta.topic + '|' + #inputMeta.partition + '|' + #inputMeta.offset + '|' + #inputMeta.timestamp")
        .buildSimpleVariable("c", "keyVariable", "#inputMeta.key.key + '|' + #inputMeta.key.timestamp")
        .buildSimpleVariable("d", "headerVariable", "#inputMeta.headers.get('first') + '|' + #inputMeta.headers.get('second')")
        .sink("out", "'[' + #inputVariable + '][' + #metaVariable + '][' + #keyVariable + '][' + #headerVariable + ']'", "sinkForStrings")

    processInvoker.invokeWithKafka(process, config) {
      eventually {
        SinkForStrings.data shouldBe List(s"[some id|some field][${Topic}|0|0|888][some key|123][header value|null]")
      }
    }

  }

  protected override def beforeAll(): Unit = {
    super.beforeAll()
    kafkaClient.createTopic(Topic, partitions = 1)
  }
}
