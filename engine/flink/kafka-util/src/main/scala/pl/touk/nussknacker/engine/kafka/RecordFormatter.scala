package pl.touk.nussknacker.engine.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}

/**
  * It is interface for bi-directional conversion between Kafka record and bytes. It is used when data
  * stored on topic aren't in human readable format and you need to add extra step in generation of test data
  * and in reading of these data.
  */
trait RecordFormatter {

  protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte]

  protected def parseRecord(topic: String, bytes: Array[Byte]): ProducerRecord[Array[Byte], Array[Byte]]

  protected def testDataSplit: TestDataSplit

  def prepareGeneratedTestData(records: List[ConsumerRecord[Array[Byte], Array[Byte]]]): Array[Byte] = {
    testDataSplit.joinData(records.map(formatRecord))
  }

  def parseDataForTest(topic: String, mergedData: Array[Byte]): List[ProducerRecord[Array[Byte], Array[Byte]]] = {
    testDataSplit.splitData(mergedData).map { formatted =>
      parseRecord(topic, formatted)
    }
  }

}

object BasicFormatter extends BasicFormatter

trait BasicFormatter extends RecordFormatter {

  override def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = record.value()

  override def parseRecord(topic: String, bytes: Array[Byte]): ProducerRecord[Array[Byte], Array[Byte]] =
    new ProducerRecord[Array[Byte], Array[Byte]](topic, bytes)

  override def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit
}