package pl.touk.nussknacker.engine.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}

/**
  * It is interface for bi-directional conversion between Kafka record and bytes. It is used when data
  * stored on topic aren't in human readable format and you need to add extra step in generation of test data
  * and in reading of these data.
  */
trait RecordFormatter extends Serializable {

  protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte]

  protected def parseRecord(topic: String, bytes: Array[Byte]): ConsumerRecord[Array[Byte], Array[Byte]]

  protected def testDataSplit: TestDataSplit

  def prepareGeneratedTestData(records: List[ConsumerRecord[Array[Byte], Array[Byte]]]): Array[Byte] = {
    testDataSplit.joinData(records.map(formatRecord))
  }

  def parseDataForTest(topic: String, mergedData: Array[Byte]): List[ConsumerRecord[Array[Byte], Array[Byte]]] = {
    testDataSplit.splitData(mergedData).map { formatted =>
      parseRecord(topic, formatted)
    }
  }

}

case class BasicRecordFormatter(override val testDataSplit: TestDataSplit) extends RecordFormatter {

  override def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = record.value()

  override def parseRecord(topic: String, bytes: Array[Byte]): ConsumerRecord[Array[Byte], Array[Byte]] =
    new ConsumerRecord[Array[Byte], Array[Byte]](topic, 0, 0L, Array[Byte](), bytes)

}

object BasicRecordFormatter {
  def apply(): BasicRecordFormatter =
    BasicRecordFormatter(TestParsingUtils.newLineSplit)
}
