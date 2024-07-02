package pl.touk.nussknacker.engine.kafka.consumerrecord

import cats.data.NonEmptyList
import io.circe.Json
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.BeforeAndAfterAll
import org.scalatest.LoneElement._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.test.TestRecord
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryMixin._
import pl.touk.nussknacker.engine.kafka.source.flink.{
  KafkaSourceFactoryMixin,
  SampleConsumerRecordDeserializationSchemaFactory
}

import java.nio.charset.StandardCharsets
import java.util.Optional

class ConsumerRecordToJsonFormatterSpec
    extends AnyFunSuite
    with Matchers
    with KafkaSpec
    with BeforeAndAfterAll
    with KafkaSourceFactoryMixin {

  private val topic = TopicName.ForSource("dummyTopic")

  private val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(
    keyDeserializer = sampleKeyJsonDeserializer,
    valueDeserializer = sampleValueJsonDeserializer
  )

  private lazy val sampleKeyValueFormatter = new ConsumerRecordToJsonFormatterFactory[SampleKey, SampleValue]
    .create(
      kafkaConfig,
      deserializationSchemaFactory.create(NonEmptyList.one(topic), kafkaConfig)
    )

  test("check sample serializer and deserializer") {
    val (sampleKeyBytes, sampleValueBytes) = serializeKeyValue(Some(sampleKey), sampleValue)
    val resultKeyObj                       = sampleKeyJsonDeserializer.deserialize(topic.name, sampleKeyBytes)
    resultKeyObj shouldEqual sampleKey
    val resultValueObj = sampleValueJsonDeserializer.deserialize(topic.name, sampleValueBytes)
    resultValueObj shouldEqual sampleValue
  }

  test("prepare and parse test data from ConsumerRecord with key, with headers") {
    val (sampleKeyBytes, sampleValueBytes) = serializeKeyValue(Some(sampleKey), sampleValue)
    val givenObj = createConsumerRecord(
      topic = topic.name,
      partition = 11,
      offset = 22L,
      timestamp = 100L,
      timestampType = TimestampType.NO_TIMESTAMP_TYPE,
      key = sampleKeyBytes,
      value = sampleValueBytes,
      headers = sampleHeaders,
      leaderEpoch = Optional.empty[Integer]
    )
    val testRecord = sampleKeyValueFormatter.prepareGeneratedTestData(List(givenObj)).testRecords.loneElement
    val resultObj  = sampleKeyValueFormatter.parseRecord(topic, testRecord)
    checkResult(resultObj, givenObj)
  }

  test("prepare and parse test data from ConsumerRecord with key, empty headers") {
    val (sampleKeyBytes, sampleValueBytes) = serializeKeyValue(Some(sampleKey), sampleValue)
    val givenObj   = new ConsumerRecord[Array[Byte], Array[Byte]](topic.name, 11, 22L, sampleKeyBytes, sampleValueBytes)
    val testRecord = sampleKeyValueFormatter.prepareGeneratedTestData(List(givenObj)).testRecords.loneElement
    val resultObj  = sampleKeyValueFormatter.parseRecord(TopicName.ForSource("topic"), testRecord)
    checkResult(resultObj, givenObj)
  }

  test("should raise exception when key is expected and empty") {
    val (_, sampleValueBytes) = serializeKeyValue(Some(sampleKey), sampleValue)
    val givenObj =
      new ConsumerRecord[Array[Byte], Array[Byte]](topic.name, 11, 22L, Array.emptyByteArray, sampleValueBytes)
    intercept[Exception] {
      val testRecord = sampleKeyValueFormatter.prepareGeneratedTestData(List(givenObj)).testRecords.loneElement
      val resultObj  = sampleKeyValueFormatter.parseRecord(TopicName.ForSource("topic"), testRecord)
    }.getMessage should startWith("Failed to decode")
  }

  test("decode and format partially defined ConsumerRecord using default values") {
    val testRecord = TestRecord(
      Json.obj(
        "key"   -> Json.obj("partOne" -> Json.fromString("abc"), "partTwo" -> Json.fromLong(2)),
        "value" -> Json.obj("id" -> Json.fromString("def"), "field" -> Json.fromString("ghi"))
      )
    )
    val resultObj = sampleKeyValueFormatter.parseRecord(TopicName.ForSource("topic"), testRecord)
    val expectedObj = new ConsumerRecord[Array[Byte], Array[Byte]](
      "topic",
      0,
      0L,
      """{"partOne":"abc","partTwo":2}""".getBytes,
      """{"id":"def","field":"ghi"}""".getBytes
    )
    checkResult(resultObj, expectedObj)
  }

  test("decode and format basic string-and-value-only test data using default values") {
    val testRecord = TestRecord(Json.fromString("lorem ipsum"))
    val resultObj  = BasicRecordFormatter.parseRecord(TopicName.ForSource("topic"), testRecord)
    val expectedObj = new ConsumerRecord[Array[Byte], Array[Byte]](
      "topic",
      0,
      0L,
      Array.emptyByteArray,
      "lorem ipsum".getBytes(StandardCharsets.UTF_8)
    )
    checkResult(resultObj, expectedObj)
  }

}
