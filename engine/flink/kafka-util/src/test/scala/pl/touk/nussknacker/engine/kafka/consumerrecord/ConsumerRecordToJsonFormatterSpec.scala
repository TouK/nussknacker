package pl.touk.nussknacker.engine.kafka.consumerrecord

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.{Assertion, BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.SampleConsumerRecordSerializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactoryMixin._

class ConsumerRecordToJsonFormatterSpec extends FunSuite with Matchers with KafkaSpec with BeforeAndAfterAll {

  private val topic = "dummyTopic"
  private lazy val kafkaConfig = KafkaConfig.parseConfig(config)
  private val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(sampleKeyJsonDeserializer, sampleValueJsonDeserializer)
  private val serializationSchemaFactory = new SampleConsumerRecordSerializationSchemaFactory(sampleKeyJsonSerializer, sampleValueJsonSerializer)

  private lazy val sampleKeyValueFormatter = new ConsumerRecordToJsonFormatter(
    deserializationSchemaFactory.create(List(topic), kafkaConfig),
    serializationSchemaFactory.create(topic, kafkaConfig)
  )

  private lazy val basicRecordFormatter = BasicFormatter

  private val sampleKey = SampleKey("one", 2)
  private val sampleValue = SampleValue("lorem", "ipsum")
  private val sampleHeaders = ConsumerRecordUtils.toHeaders(Map("first" -> "not empty", "second" -> null))

  test("check sample serializer and deserializer") {
    val resultKeyBytes = sampleKeyJsonSerializer.serialize(topic, sampleKey)
    val resultKeyObj = sampleKeyJsonDeserializer.deserialize(topic, resultKeyBytes)
    resultKeyObj shouldEqual sampleKey
    val resultValueBytes = sampleValueJsonSerializer.serialize(topic, sampleValue)
    val resultValueObj = sampleValueJsonDeserializer.deserialize(topic, resultValueBytes)
    resultValueObj shouldEqual sampleValue
  }

  test("prepare and parse test data from ConsumerRecord with key, with headers") {
    val sampleKeyBytes = sampleKeyJsonSerializer.serialize(topic, sampleKey)
    val sampleValueBytes = sampleValueJsonSerializer.serialize(topic, sampleValue)
    val givenObj = SerializableConsumerRecord.createConsumerRecord(topic, 11, 22L,100L, sampleKeyBytes, sampleValueBytes, sampleHeaders)
    val resultBytes = sampleKeyValueFormatter.prepareGeneratedTestData(List(givenObj))
    val resultObj = sampleKeyValueFormatter.parseDataForTest(topic, resultBytes).head
    checkResult(resultObj, givenObj)
  }

  test("prepare and parse test data from ConsumerRecord with key, empty headers") {
    val sampleKeyBytes = sampleKeyJsonSerializer.serialize(topic, sampleKey)
    val sampleValueBytes = sampleValueJsonSerializer.serialize(topic, sampleValue)
    val givenObj = new ConsumerRecord[Array[Byte], Array[Byte]](topic, 11, 22L, sampleKeyBytes, sampleValueBytes)
    val resultBytes = sampleKeyValueFormatter.prepareGeneratedTestData(List(givenObj))
    val resultObj = sampleKeyValueFormatter.parseDataForTest("topic", resultBytes).head
    checkResult(resultObj, givenObj)
  }

  test("should raise exception when key is expected and empty") {
    val sampleKeyBytes = sampleKeyJsonSerializer.serialize(topic, sampleKey)
    val sampleValueBytes = sampleValueJsonSerializer.serialize(topic, sampleValue)
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

  private def checkResult(a: ConsumerRecord[Array[Byte], Array[Byte]], b: ConsumerRecord[Array[Byte], Array[Byte]]): Assertion = {
    // here ignore checksums and timestampType
    a.topic() shouldEqual b.topic()
    a.partition() shouldEqual b.partition()
    a.offset() shouldEqual b.offset()
    a.timestamp() shouldEqual b.timestamp()
    a.key() shouldEqual b.key()
    a.value() shouldEqual b.value()
    a.headers() shouldEqual b.headers()
  }
}
