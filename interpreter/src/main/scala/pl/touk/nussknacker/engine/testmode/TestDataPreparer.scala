package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.ScenarioTestData

case class ParsedTestData[T](samples: List[T])

object TestDataPreparer {

  def prepareDataForTest[T](sourceTestSupport: SourceTestSupport[T], scenarioTestData: ScenarioTestData, sourceId: NodeId): ParsedTestData[T] = {
    val testData = scenarioTestData.forSourceId(sourceId)
    val testParserForSource = sourceTestSupport.testDataParser
    val testSamples = testParserForSource.parseTestData(testData)
    if (testSamples.size > scenarioTestData.samplesLimit) {
      throw new IllegalArgumentException(s"Too many samples: ${testSamples.size}, limit is: ${scenarioTestData.samplesLimit}")
    }
    ParsedTestData(testSamples)
  }

}
