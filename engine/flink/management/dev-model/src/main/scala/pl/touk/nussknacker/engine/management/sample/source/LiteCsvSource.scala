package pl.touk.nussknacker.engine.management.sample.source

import io.circe.Json
import pl.touk.nussknacker.engine.api.{CirceUtil, Context, NodeId, VariableConstants}
import pl.touk.nussknacker.engine.api.process.{SourceTestSupport, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.lite.api.utils.sources.BaseLiteSource
import pl.touk.nussknacker.engine.management.sample.dto.CsvRecord

class LiteCsvSource(override val nodeId: NodeId)
    extends BaseLiteSource[CsvRecord]
    with SourceTestSupport[CsvRecord]
    with TestDataGenerator {

  override def transform(record: CsvRecord): Context =
    Context(contextIdGenerator.nextContextId())
      .withVariable(VariableConstants.InputVariableName, record)

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

}
