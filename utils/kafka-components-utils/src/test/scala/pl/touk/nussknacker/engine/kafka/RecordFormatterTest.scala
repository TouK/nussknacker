package pl.touk.nussknacker.engine.kafka

import io.circe.Json
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.test.TestRecord

import java.util.Optional

class RecordFormatterTest extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  object EmptyRecordFormatter extends RecordFormatter {

    override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): TestRecord =
      TestRecord(Json.Null)

    override def parseRecord(
        topic: TopicName.ForSource,
        testRecord: TestRecord
    ): ConsumerRecord[Array[Byte], Array[Byte]] = ???

  }

  object EmptyWithTimestampRecordFormatter extends RecordFormatter {
    val customTimestamp = 42

    override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): TestRecord =
      TestRecord(Json.Null, timestamp = Some(customTimestamp))

    override def parseRecord(
        topic: TopicName.ForSource,
        testRecord: TestRecord
    ): ConsumerRecord[Array[Byte], Array[Byte]] = ???

  }

  private val consumerRecords = List(
    createConsumerRecordWithTimestamp(10),
    createConsumerRecordWithTimestamp(ConsumerRecord.NO_TIMESTAMP),
  )

  test("should set test record timestamp based on consumer record") {
    val testRecords = EmptyRecordFormatter.prepareGeneratedTestData(consumerRecords).testRecords

    testRecords.map(_.timestamp) shouldBe List(Some(10), None)
  }

  test("should not overwrite test record timestamp if set") {
    val testRecords = EmptyWithTimestampRecordFormatter.prepareGeneratedTestData(consumerRecords).testRecords

    testRecords.map(_.timestamp) shouldBe List(
      Some(EmptyWithTimestampRecordFormatter.customTimestamp),
      Some(EmptyWithTimestampRecordFormatter.customTimestamp)
    )
  }

  private def createConsumerRecordWithTimestamp(timestamp: java.lang.Long): ConsumerRecord[Array[Byte], Array[Byte]] = {
    new ConsumerRecord[Array[Byte], Array[Byte]](
      "topic",
      0,
      0,
      timestamp,
      TimestampType.CREATE_TIME,
      0,
      0,
      Array.empty[Byte],
      Array.empty[Byte],
      new RecordHeaders(),
      Optional.empty[java.lang.Integer]()
    )
  }

}
