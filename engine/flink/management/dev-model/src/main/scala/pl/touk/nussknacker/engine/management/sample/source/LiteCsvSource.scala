package pl.touk.nussknacker.engine.management.sample.source

import io.circe.Json
import pl.touk.nussknacker.engine.api.{CirceUtil, NodeId, VariableConstants}
import pl.touk.nussknacker.engine.api.livedata.{DataRecord, DataRecords, LiveDataProvider}
import pl.touk.nussknacker.engine.api.process.{ContextVariables, SourceTestSupport, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.{typing, ReturningType}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.lite.api.utils.sources.BaseLiteSource

import java.util.{List => JList}
import scala.jdk.CollectionConverters._

class LiteCsvSource(override val nodeId: NodeId)
// For the lite variant of csv source we can't use Array[List] because during test data decoding, is used generic json decoder
// see notice next to TestRunner.decodeCommonFormatRecords
    extends BaseLiteSource[JList[String]]
    with SourceTestSupport[JList[String]]
    with TestDataGenerator
    with LiveDataProvider
    with ReturningType {

  override def transform(record: JList[String]): ContextVariables =
    ContextVariables(Map(VariableConstants.InputVariableName -> record))

  override def fetchLiveData(maxNumberOfRecords: Int): DataRecords = DataRecords(
    List(
      DataRecord(
        Map(VariableConstants.InputVariableName -> List("record1", "field2").asJava),
        upstreamTimestamp = None
      ),
      DataRecord(Map(VariableConstants.InputVariableName -> List("record2", "field3").asJava), upstreamTimestamp = None)
    )
  )

  override def generateTestData(size: Int): TestData = TestData(
    List(
      TestRecord(Json.fromString("record1|field2")),
      TestRecord(Json.fromString("record2|field3")),
    )
  )

  override def testRecordParser: TestRecordParser[JList[String]] = (testRecords: List[TestRecord]) =>
    testRecords.map { testRecord =>
      CirceUtil.decodeJsonUnsafe[String](testRecord.json).split("\\|").toList.asJava
    }

  override def returnType: typing.TypingResult = Typed.fromDetailedType[JList[String]]

}
