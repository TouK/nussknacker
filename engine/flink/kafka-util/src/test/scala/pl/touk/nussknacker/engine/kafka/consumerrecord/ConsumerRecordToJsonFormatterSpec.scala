package pl.touk.nussknacker.engine.kafka.consumerrecord

import java.util.Optional
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactoryMixin._
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.source.{KafkaSourceFactoryMixin, SampleConsumerRecordDeserializationSchemaFactory}

class ConsumerRecordToJsonFormatterSpec extends FunSuite with Matchers with KafkaSpec with BeforeAndAfterAll with KafkaSourceFactoryMixin {

  private val topic = "dummyTopic"
  private val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(sampleKeyJsonDeserializer, sampleValueJsonDeserializer)

  private lazy val sampleKeyValueFormatter =
    ConsumerRecordToJsonFormatterFactory[SampleKey, SampleValue].create(
      kafkaConfig,
      deserializationSchemaFactory.create(List(topic), kafkaConfig)
    )

  private lazy val basicRecordFormatter = BasicRecordFormatter()

  test("check sample serializer and deserializer") {
    val (sampleKeyBytes, sampleValueBytes) = serializeKeyValue(Some(sampleKey), sampleValue)
    val resultKeyObj = sampleKeyJsonDeserializer.deserialize(topic, sampleKeyBytes)
    resultKeyObj shouldEqual sampleKey
    val resultValueObj = sampleValueJsonDeserializer.deserialize(topic, sampleValueBytes)
    resultValueObj shouldEqual sampleValue
  }

  test("prepare and parse test data from ConsumerRecord with key, with headers") {
    val (sampleKeyBytes, sampleValueBytes) = serializeKeyValue(Some(sampleKey), sampleValue)
    val givenObj = createConsumerRecord(topic, 11, 22L,100L, TimestampType.NO_TIMESTAMP_TYPE, sampleKeyBytes, sampleValueBytes, sampleHeaders, Optional.empty[Integer])
    val resultBytes = sampleKeyValueFormatter.prepareGeneratedTestData(List(givenObj))
    val resultObj = sampleKeyValueFormatter.parseDataForTest(topic, resultBytes).head
    checkResult(resultObj, givenObj)
  }

  test("prepare and parse test data from ConsumerRecord with key, empty headers") {
    val (sampleKeyBytes, sampleValueBytes) = serializeKeyValue(Some(sampleKey), sampleValue)
    val givenObj = new ConsumerRecord[Array[Byte], Array[Byte]](topic, 11, 22L, sampleKeyBytes, sampleValueBytes)
    val resultBytes = sampleKeyValueFormatter.prepareGeneratedTestData(List(givenObj))
    val resultObj = sampleKeyValueFormatter.parseDataForTest("topic", resultBytes).head
    checkResult(resultObj, givenObj)
  }

  test("should raise exception when key is expected and empty") {
    val (_, sampleValueBytes) = serializeKeyValue(Some(sampleKey), sampleValue)
    val givenObj = new ConsumerRecord[Array[Byte], Array[Byte]](topic, 11, 22L, Array.emptyByteArray, sampleValueBytes)
    intercept[Exception] {
      val resultBytes = sampleKeyValueFormatter.prepareGeneratedTestData(List(givenObj))
      val resultObj = sampleKeyValueFormatter.parseDataForTest("topic", resultBytes).head
    }.getMessage should startWith("Failed to decode")
  }

  test("decode and format partially defined ConsumerRecord using default values") {
    val givenBytes = new String("""{"key":{"partOne":"abc", "partTwo":2}, "value":{"id":"def", "field":"ghi"}}""").getBytes
    val resultObj = sampleKeyValueFormatter.parseDataForTest("topic", givenBytes).head
    val expectedObj = new ConsumerRecord[Array[Byte], Array[Byte]]("topic", 0, 0L, """{"partOne":"abc","partTwo":2}""".getBytes, """{"id":"def","field":"ghi"}""".getBytes)
    checkResult(resultObj, expectedObj)
  }

  test("decode and format basic string-and-value-only test data using default values") {
    val givenBytes = new String("lorem ipsum").getBytes
    val resultObj = basicRecordFormatter.parseDataForTest("topic", givenBytes).head
    val expectedObj = new ConsumerRecord[Array[Byte], Array[Byte]]("topic", 0, 0L, Array.emptyByteArray, givenBytes)
    checkResult(resultObj, expectedObj)
  }

}
