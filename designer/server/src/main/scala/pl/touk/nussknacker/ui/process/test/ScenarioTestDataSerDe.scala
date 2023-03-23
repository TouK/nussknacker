package pl.touk.nussknacker.ui.process.test

import io.circe.parser
import pl.touk.nussknacker.engine.definition.test.{PreliminaryScenarioTestData, PreliminaryScenarioTestRecord}
import pl.touk.nussknacker.ui.api.TestDataSettings

class ScenarioTestDataSerDe(testDataSettings: TestDataSettings) {

  def serialize(scenarioTestData: PreliminaryScenarioTestData): Either[String, RawScenarioTestData] = {
    import io.circe.syntax._

    val content = scenarioTestData.testRecords
      .map(_.asJson.noSpaces)
      .mkString("\n")
    Either.cond(content.length <= testDataSettings.testDataMaxLength,
      RawScenarioTestData(content),
      s"Too much data generated, limit is: ${testDataSettings.testDataMaxLength}")
  }

  def deserialize(rawTestData: RawScenarioTestData): Either[String, PreliminaryScenarioTestData] = {
    import cats.implicits.catsStdInstancesForEither
    import cats.syntax.either._
    import cats.syntax.traverse._

    val rawRecords = rawTestData.content.linesIterator.toList
    val limitedRawRecords = Either.cond(rawRecords.size <= testDataSettings.maxSamplesCount,
      rawRecords,
      s"Too many samples: ${rawRecords.size}, limit is: ${testDataSettings.maxSamplesCount}")
    val records: Either[String, List[PreliminaryScenarioTestRecord]] = limitedRawRecords.flatMap { rawRecords =>
      rawRecords.map { rawTestRecord =>
        val record = parser.decode[PreliminaryScenarioTestRecord](rawTestRecord)
        record.leftMap(_ => s"Could not parse record: '$rawTestRecord'")
      }.sequence
    }
    records.map(PreliminaryScenarioTestData)
  }

}
