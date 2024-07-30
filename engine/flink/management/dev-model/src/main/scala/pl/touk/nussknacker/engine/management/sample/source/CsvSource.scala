package pl.touk.nussknacker.engine.management.sample.source

import com.github.ghik.silencer.silent
import io.circe.Json
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.process.TestDataGenerator
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkSourceTestSupport,
  StandardFlinkSource,
  StandardFlinkSourceFunctionUtils
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.management.sample.dto.CsvRecord

class CsvSource extends StandardFlinkSource[CsvRecord] with FlinkSourceTestSupport[CsvRecord] with TestDataGenerator {

  @silent("deprecated")
  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[CsvRecord] = StandardFlinkSourceFunctionUtils.createSourceStream(
    env = env,
    sourceFunction = new SourceFunction[CsvRecord] {
      override def cancel(): Unit = {}

      override def run(ctx: SourceContext[CsvRecord]): Unit = {}
    },
    typeInformation = typeInformation
  )

  override val typeInformation: TypeInformation[CsvRecord] = TypeInformation.of(classOf[CsvRecord])

  override def generateTestData(size: Int): TestData = TestData(
    List(
      TestRecord(Json.fromString("record1|field2")),
      TestRecord(Json.fromString("record2|field3")),
    )
  )

  override def testRecordParser: TestRecordParser[CsvRecord] = (testRecords: List[TestRecord]) =>
    testRecords.map { testRecord =>
      CsvRecord(CirceUtil.decodeJsonUnsafe[String](testRecord.json).split("\\|").toList)
    }

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[CsvRecord]] = timestampAssigner
}
