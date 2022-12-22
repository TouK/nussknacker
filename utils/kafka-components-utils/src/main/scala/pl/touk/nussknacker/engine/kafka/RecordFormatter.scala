package pl.touk.nussknacker.engine.kafka

import io.circe.Json
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.process.{Source, SourceTestSupport, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}

import java.nio.charset.StandardCharsets

/**
  * It is interface for bi-directional conversion between Kafka record and [[TestRecord]]. It is used when data
  * stored on topic aren't in human readable format and you need to add extra step in generation of test data
  * and in reading of these data.
  */
trait RecordFormatter extends Serializable {

  protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): TestRecord

  def parseRecord(topic: String, testRecord: TestRecord): ConsumerRecord[Array[Byte], Array[Byte]]

  def prepareGeneratedTestData(records: List[ConsumerRecord[Array[Byte], Array[Byte]]]): TestData = {
    import RecordFormatter.RichTestRecord

    val testRecords = records.map { consumerRecord =>
      val testRecord = formatRecord(consumerRecord)
      testRecord.fillEmptyTimestampFromConsumerRecord(consumerRecord)
    }
    TestData(testRecords)
  }

  def generateTestData(topics: List[String], size: Int, kafkaConfig: KafkaConfig): TestData = {
    val listsFromAllTopics = topics.map(KafkaUtils.readLastMessages(_, size, kafkaConfig))
    val merged = ListUtil.mergeListsFromTopics(listsFromAllTopics, size)
    prepareGeneratedTestData(merged)
  }

}

object RecordFormatter {

  private implicit class RichTestRecord(testRecord: TestRecord) {

    def fillEmptyTimestampFromConsumerRecord(consumerRecord: ConsumerRecord[_, _]): TestRecord = {
      testRecord.timestamp match {
        case Some(_) => testRecord
        case None => testRecord.copy(timestamp = getConsumerRecordTimestamp(consumerRecord))
      }
    }

    private def getConsumerRecordTimestamp(consumerRecord: ConsumerRecord[_, _]): Option[Long] = {
      Option(consumerRecord.timestamp()).filterNot(_ == ConsumerRecord.NO_TIMESTAMP)
    }

  }

}

object BasicRecordFormatter extends RecordFormatter {

  override def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): TestRecord =
    TestRecord(Json.fromString(new String(record.value(), StandardCharsets.UTF_8)))

  override def parseRecord(topic: String, testRecord: TestRecord): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val stringRecord = CirceUtil.decodeJsonUnsafe[String](testRecord.json)
    new ConsumerRecord[Array[Byte], Array[Byte]](topic, 0, 0L, Array[Byte](), stringRecord.getBytes(StandardCharsets.UTF_8))
  }

}

trait RecordFormatterBaseTestDataGenerator extends TestDataGenerator { self: Source with SourceTestSupport[_] =>

  protected def kafkaConfig: KafkaConfig

  protected def topics: List[String]

  protected def formatter: RecordFormatter

  override def generateTestData(size: Int): TestData = formatter.generateTestData(topics, size, kafkaConfig)

}
