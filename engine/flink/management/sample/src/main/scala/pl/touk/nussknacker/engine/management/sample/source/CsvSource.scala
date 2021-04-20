package pl.touk.nussknacker.engine.management.sample.source

import java.nio.charset.StandardCharsets

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import pl.touk.nussknacker.engine.api.process.TestDataGenerator
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.management.sample.dto.CsvRecord
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

class CsvSource extends BasicFlinkSource[CsvRecord] with FlinkSourceTestSupport[CsvRecord] with TestDataGenerator {

  override val typeInformation: TypeInformation[CsvRecord] = implicitly[TypeInformation[CsvRecord]]

  override def flinkSourceFunction: SourceFunction[CsvRecord] = new SourceFunction[CsvRecord] {
    override def cancel(): Unit = {}
    override def run(ctx: SourceContext[CsvRecord]): Unit = {}
  }

  override def generateTestData(size: Int): Array[Byte] = "record1|field2\nrecord2|field3".getBytes(StandardCharsets.UTF_8)

  override def testDataParser: TestDataParser[CsvRecord] = new NewLineSplittedTestDataParser[CsvRecord] {
    override def parseElement(testElement: String): CsvRecord = CsvRecord(testElement.split("\\|").toList)
  }

  override def timestampAssigner: Option[Nothing] = None

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[CsvRecord]] = timestampAssigner
}
