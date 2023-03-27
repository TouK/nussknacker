package pl.touk.nussknacker.engine.kafka.generic

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.test.TestRecord
import pl.touk.nussknacker.engine.kafka.{BasicRecordFormatter, RecordFormatter}
import pl.touk.nussknacker.engine.kafka.serialization.schemas.toJson

import java.nio.charset.StandardCharsets

object JsonRecordFormatter extends RecordFormatter {

  override def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): TestRecord =
    TestRecord(toJson(record.value()))

  override def parseRecord(topic: String, testRecord: TestRecord): ConsumerRecord[Array[Byte], Array[Byte]] = {
    new ConsumerRecord[Array[Byte], Array[Byte]](topic, 0, 0L, Array[Byte](), testRecord.json.noSpaces.getBytes(StandardCharsets.UTF_8))
  }

}
