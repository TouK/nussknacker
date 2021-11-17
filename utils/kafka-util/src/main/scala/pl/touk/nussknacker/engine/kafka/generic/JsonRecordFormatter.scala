package pl.touk.nussknacker.engine.kafka.generic

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.kafka.{BasicRecordFormatter, RecordFormatter}
import pl.touk.nussknacker.engine.kafka.serialization.schemas.toJson

import java.nio.charset.StandardCharsets

//We format before returning to user, to avoid problems with empty lines etc.
object JsonRecordFormatter extends RecordFormatter {

  private val basicRecordFormatter = BasicRecordFormatter(TestParsingUtils.emptyLineSplit)

  override def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] =
    toJson(record.value()).spaces2.getBytes(StandardCharsets.UTF_8)

  override protected def parseRecord(topic: String, bytes: Array[Byte]): ConsumerRecord[Array[Byte], Array[Byte]] =
    basicRecordFormatter.parseRecord(topic, bytes)

  override def testDataSplit: TestDataSplit =
    basicRecordFormatter.testDataSplit

}