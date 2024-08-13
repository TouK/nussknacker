package pl.touk.nussknacker.engine.kafka

import cats.data.NonEmptyList
import io.circe.Json
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.process.{Source, SourceTestSupport, TestDataGenerator, TopicName}
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}
import pl.touk.nussknacker.engine.util.ListUtil

import java.nio.charset.StandardCharsets

/**
  * It is interface for bi-directional conversion between Kafka record and [[TestRecord]]. It is used when data
  * stored on topic aren't in human readable format and you need to add extra step in generation of test data
  * and in reading of these data.
  */
trait RecordFormatter extends Serializable {

  protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): TestRecord

  def parseRecord(topic: TopicName.ForSource, testRecord: TestRecord): ConsumerRecord[Array[Byte], Array[Byte]]

  def prepareGeneratedTestData(records: List[ConsumerRecord[Array[Byte], Array[Byte]]]): TestData = {
    val testRecords = records.map { consumerRecord =>
      val testRecord = formatRecord(consumerRecord)
      fillEmptyTimestampFromConsumerRecord(testRecord, consumerRecord)
    }
    TestData(testRecords)
  }

  def generateTestData(topics: NonEmptyList[TopicName.ForSource], size: Int, kafkaConfig: KafkaConfig): TestData = {
    val listsFromAllTopics = topics.map(KafkaUtils.readLastMessages(_, size, kafkaConfig))
    val merged             = ListUtil.mergeLists(listsFromAllTopics.toList, size)
    prepareGeneratedTestData(merged)
  }

  private def fillEmptyTimestampFromConsumerRecord(
      testRecord: TestRecord,
      consumerRecord: ConsumerRecord[_, _]
  ): TestRecord = {
    testRecord.timestamp match {
      case Some(_) => testRecord
      case None    => testRecord.copy(timestamp = getConsumerRecordTimestamp(consumerRecord))
    }
  }

  private def getConsumerRecordTimestamp(consumerRecord: ConsumerRecord[_, _]): Option[Long] = {
    Option(consumerRecord.timestamp()).filterNot(_ == ConsumerRecord.NO_TIMESTAMP)
  }

}

object BasicRecordFormatter extends RecordFormatter {

  override def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): TestRecord =
    TestRecord(Json.fromString(new String(record.value(), StandardCharsets.UTF_8)))

  override def parseRecord(
      topic: TopicName.ForSource,
      testRecord: TestRecord
  ): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val stringRecord = CirceUtil.decodeJsonUnsafe[String](testRecord.json)
    new ConsumerRecord[Array[Byte], Array[Byte]](
      topic.name,
      0,
      0L,
      Array[Byte](),
      stringRecord.getBytes(StandardCharsets.UTF_8)
    )
  }

}

trait RecordFormatterBaseTestDataGenerator extends TestDataGenerator { self: Source with SourceTestSupport[_] =>

  protected def kafkaConfig: KafkaConfig

  protected def topics: NonEmptyList[TopicName.ForSource]

  protected def formatter: RecordFormatter

  override def generateTestData(size: Int): TestData = formatter.generateTestData(topics, size, kafkaConfig)

}
