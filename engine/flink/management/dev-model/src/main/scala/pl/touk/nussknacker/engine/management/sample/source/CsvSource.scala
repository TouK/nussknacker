package pl.touk.nussknacker.engine.management.sample.source

import com.github.ghik.silencer.silent
import io.circe.Json

import java.nio.charset.StandardCharsets
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.process.TestDataGenerator
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.management.sample.dto.CsvRecord
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

class CsvSource extends BasicFlinkSource[CsvRecord] with FlinkSourceTestSupport[CsvRecord] with TestDataGenerator {

  override val typeInformation: TypeInformation[CsvRecord] = TypeInformation.of(classOf[CsvRecord])

  @silent("deprecated")
  override def flinkSourceFunction: SourceFunction[CsvRecord] = new SourceFunction[CsvRecord] {
    override def cancel(): Unit                           = {}
    override def run(ctx: SourceContext[CsvRecord]): Unit = {}
  }

  override def generateTestData(size: Int): TestData = TestData(
    List(
      TestRecord(Json.fromString("record1|field2")),
      TestRecord(Json.fromString("record2|field3")),
    )
  )

  override def testRecordParser: TestRecordParser[CsvRecord] =
    (testRecord: TestRecord) => CsvRecord(CirceUtil.decodeJsonUnsafe[String](testRecord.json).split("\\|").toList)

  override def timestampAssigner: Option[Nothing] = None

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[CsvRecord]] = timestampAssigner
}
