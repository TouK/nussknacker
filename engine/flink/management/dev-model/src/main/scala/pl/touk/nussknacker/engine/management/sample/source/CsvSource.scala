package pl.touk.nussknacker.engine.management.sample.source

import io.circe.Json
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import pl.touk.nussknacker.engine.api.{CirceUtil, Context, VariableConstants}
import pl.touk.nussknacker.engine.api.livedata.{DataRecord, DataRecords, LiveDataProvider}
import pl.touk.nussknacker.engine.api.process.TestDataGenerator
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.{typing, ReturningType}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process._

import scala.annotation.nowarn

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
    sourceStream(env)
      .setUidAndNameToNodeId(flinkNodeContext.nodeId)
      .map(
        new FlinkContextInitializingFunction(
          contextInitializer,
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext
        ),
        flinkNodeContext.contextTypeInfo
      )
  }

  @nowarn("cat=deprecation")
  private def sourceStream(
      env: StreamExecutionEnvironment,
  ): DataStreamSource[Array[String]] = {
    env.addSource(
      new SourceFunction[Array[String]] {
        override def cancel(): Unit = {}

        override def run(ctx: SourceContext[Array[String]]): Unit = {}
      },
      TypeInformation.of(classOf[Array[String]])
    )
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
