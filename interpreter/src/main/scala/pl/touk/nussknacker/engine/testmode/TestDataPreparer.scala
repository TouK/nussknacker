package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.{Source, SourceTestSupport}
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, TestRecord}

case class ParsedTestData[T](samples: List[T])

object TestDataPreparer {

  def prepareDataForTest[T](sourceTestSupport: SourceTestSupport[T], scenarioTestData: ScenarioTestData, sourceId: NodeId): ParsedTestData[T] = {
    val testParserForSource = sourceTestSupport.testRecordParser
    val testRecordsForSource = scenarioTestData.testRecords.filter(_.sourceId == sourceId).map(_.record)
    val testSamples = testRecordsForSource.map(testParserForSource.parse)
    ParsedTestData(testSamples)
  }

  def prepareRecordForTest[T](source: Source, testRecord: TestRecord): T = {
    val sourceTestSupport = source match {
      case e: SourceTestSupport[T@unchecked] => e
      case other => throw new IllegalArgumentException(s"Source ${other.getClass} cannot be stubbed - it doesn't provide test data parser")
    }
    sourceTestSupport.testRecordParser.parse(testRecord)
  }

}
