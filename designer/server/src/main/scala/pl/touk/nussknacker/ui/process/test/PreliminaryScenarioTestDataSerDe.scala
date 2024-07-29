package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import io.circe.parser
import pl.touk.nussknacker.engine.definition.test.{PreliminaryScenarioTestData, PreliminaryScenarioTestRecord}
import pl.touk.nussknacker.ui.api.TestDataSettings

class PreliminaryScenarioTestDataSerDe(testDataSettings: TestDataSettings) {

  def serialize(scenarioTestData: PreliminaryScenarioTestData): Either[String, RawScenarioTestData] = {
    import io.circe.syntax._

    val content = scenarioTestData.testRecords
      .map(_.asJson.noSpaces)
      .toList
      .mkString("\n")
    Either.cond(
      content.length <= testDataSettings.testDataMaxLength,
      RawScenarioTestData(content),
      s"Too much data generated, limit is: ${testDataSettings.testDataMaxLength}"
    )
  }

  def deserialize(rawTestData: RawScenarioTestData): Either[String, PreliminaryScenarioTestData] = {
    import cats.implicits.catsStdInstancesForEither
    import cats.syntax.either._
    import cats.syntax.traverse._

    val rawRecords = rawTestData.content.linesIterator.toList
    for {
      _ <- Either.cond(
        rawRecords.size <= testDataSettings.maxSamplesCount,
        (),
        s"Too many samples: ${rawRecords.size}, limit is: ${testDataSettings.maxSamplesCount}"
      )
      decodedRecords <- rawRecords.map { rawTestRecord =>
        val record = parser.decode[PreliminaryScenarioTestRecord](rawTestRecord)
        record.leftMap(_ => s"Could not parse record: '$rawTestRecord'")
      }.sequence
      result <- NonEmptyList
        .fromList(decodedRecords)
        .map(nel => Right(PreliminaryScenarioTestData(nel)))
        .getOrElse(Left("Empty list of records"))
    } yield result
  }

}
