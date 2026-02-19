package pl.touk.nussknacker.engine.management.sample.source

import io.circe.Json
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.connector.source._
import org.apache.flink.core.io.InputStatus
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.{CirceUtil, Context, VariableConstants}
import pl.touk.nussknacker.engine.api.livedata.{DataRecord, DataRecords, LiveDataProvider}
import pl.touk.nussknacker.engine.api.process.TestDataGenerator
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.{typing, ReturningType}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.source.SingleSplitSource
import pl.touk.nussknacker.engine.flink.util.source.SingleSplitSource.SingleSplit

class CsvSource
    extends FlinkSource
    with CustomizableContextInitializerSource[Array[String]]
    with FlinkSourceTestSupport[Array[String]]
    with TestDataGenerator
    with LiveDataProvider
    with ReturningType {

  override final def contextStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    env
      .fromSource(
        flinkSource,
        WatermarkStrategy.noWatermarks(),
        flinkNodeContext.nodeId.value,
        TypeInformation.of(classOf[Array[String]])
      )
      .uid(flinkNodeContext.nodeId.value)
      .map(
        new FlinkContextInitializingFunction(
          contextInitializer,
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext
        ),
        flinkNodeContext.contextTypeInfo
      )
  }

  private def flinkSource: Source[Array[String], _, _] = {
    new SingleSplitSource[Array[String]] {
      override def getBoundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED

      override def createReader(readerContext: SourceReaderContext): SourceReader[Array[String], SingleSplit] =
        new SingleSplitSource.Reader[Array[String]] {
          override def pollNext(output: ReaderOutput[Array[String]]): InputStatus = InputStatus.END_OF_INPUT
        }

    }
  }

  override def fetchLiveData(maxNumberOfRecords: Int): DataRecords = DataRecords(
    List(
      DataRecord(Map(VariableConstants.InputVariableName -> Array("record1", "field2")), upstreamTimestamp = None),
      DataRecord(Map(VariableConstants.InputVariableName -> Array("record2", "field3")), upstreamTimestamp = None)
    )
  )

  override def generateTestData(size: Int): TestData = TestData(
    List(
      TestRecord(Json.fromString("record1|field2")),
      TestRecord(Json.fromString("record2|field3")),
    )
  )

  override def testRecordParser: TestRecordParser[Array[String]] = (testRecords: List[TestRecord]) =>
    testRecords.map { testRecord =>
      CirceUtil.decodeJsonUnsafe[String](testRecord.json).split("\\|")
    }

  override def watermarkStrategyForTest: Option[WatermarkStrategy[Array[String]]] = None

  override def returnType: typing.TypingResult = Typed.fromDetailedType[Array[String]]

}
