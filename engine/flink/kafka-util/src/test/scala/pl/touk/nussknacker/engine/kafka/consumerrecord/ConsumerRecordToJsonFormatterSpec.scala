package pl.touk.nussknacker.engine.kafka.consumerrecord

import com.github.ghik.silencer.silent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.kafka.ConsumerRecordUtils

import scala.annotation.nowarn

class ConsumerRecordToJsonFormatterSpec extends FunSuite with Matchers {

  private val formatter = new ConsumerRecordToJsonFormatter

  private val sampleKey = "Lorem ipsum"
  private val sampleValue = "dolor sit amet"
  private val sampleHeaders = ConsumerRecordUtils.toHeaders(Map("first" -> "notempty", "second" -> null))

  test("prepare and parse test data from ConsumerRecord with key, with headers") {
    val givenObj = new ConsumerRecord[Array[Byte], Array[Byte]](
      "topic",
      11,
      22L,
      100L,
      TimestampType.CREATE_TIME,
      200L,
      sampleKey.getBytes.length,
      sampleValue.getBytes.length,
      sampleKey.getBytes,
      sampleValue.getBytes,
      sampleHeaders,
      java.util.Optional.of(300)
    )
    val resultBytes = formatter.prepareGeneratedTestData(List(givenObj))
    val resultObj = formatter.parseDataForTest("topic", resultBytes).head
    checkResult(resultObj, givenObj)
  }

  test("prepare and parse test data from ConsumerRecord with key, empty headers") {
    val givenObj = new ConsumerRecord[Array[Byte], Array[Byte]](
      "topic",
      11,
      22L,
      sampleKey.getBytes,
      sampleValue.getBytes
    )
    val resultBytes = formatter.prepareGeneratedTestData(List(givenObj))
    val resultObj = formatter.parseDataForTest("topic", resultBytes).head
    checkResult(resultObj, givenObj)
  }

  test("prepare and parse test data from ConsumerRecord with empty key, empty headers") {
    val givenObj = new ConsumerRecord[Array[Byte], Array[Byte]](
      "topic",
      11,
      22L,
      null,
      sampleValue.getBytes
    )
    val resultBytes = formatter.prepareGeneratedTestData(List(givenObj))
    val resultObj = formatter.parseDataForTest("topic", resultBytes).head
    checkResult(resultObj, givenObj)
  }

  @silent("deprecated")
  @nowarn("cat=deprecation")
  private def checkResult(a: ConsumerRecord[Array[Byte], Array[Byte]], b: ConsumerRecord[Array[Byte], Array[Byte]]) = {
    a.topic() shouldEqual b.topic()
    a.partition() shouldEqual b.partition()
    a.offset() shouldEqual b.offset()
    a.timestamp() shouldEqual b.timestamp()
    a.timestampType() shouldEqual b.timestampType()
    a.checksum() shouldEqual b.checksum()
    a.serializedKeySize() shouldEqual b.serializedKeySize()
    a.serializedValueSize() shouldEqual b.serializedValueSize()
    a.key() shouldEqual b.key()
    a.value() shouldEqual b.value()
    a.headers() shouldEqual b.headers()
    a.leaderEpoch() shouldEqual b.leaderEpoch()
  }
}
