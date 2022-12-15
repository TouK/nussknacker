package pl.touk.nussknacker.ui.process.test

import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using
import io.circe.parser
import pl.touk.nussknacker.ui.api.TestDataSettings

// TODO multiple-sources-test: switch TestData to ScenarioTestData
class ScenarioTestDataSerDe(testDataSettings: TestDataSettings) {

  def serializeTestData(testData: TestData): Either[String, RawScenarioTestData] = {
    val content = testData.testRecords
      .map(_.json.noSpaces)
      .mkString("\n")
      .getBytes(StandardCharsets.UTF_8)
    Either.cond(content.size <= testDataSettings.testDataMaxBytes,
      RawScenarioTestData(content),
      s"Too much data generated, limit is: ${testDataSettings.testDataMaxBytes}")
  }

  def prepareTestData(rawTestData: RawScenarioTestData): Either[String, TestData] = {
    import cats.syntax.either._
    import cats.syntax.traverse._
    import cats.implicits.catsStdInstancesForEither

    Using(Source.fromBytes(rawTestData.content, StandardCharsets.UTF_8.name())) { source =>
      val rawTestRecords = source
        .getLines()
        .toList
      val limitedRawTestRecords = Either.cond(rawTestRecords.size <= testDataSettings.maxSamplesCount,
        rawTestRecords,
        s"Too many samples: ${rawTestRecords.size}, limit is: ${testDataSettings.maxSamplesCount}")
      val testRecords: Either[String, List[TestRecord]] = limitedRawTestRecords.flatMap { rawTestRecords =>
        rawTestRecords.map { rawTestRecord =>
          val jsonTestRecord = parser.parse(rawTestRecord)
          jsonTestRecord
            .map(TestRecord)
            .leftMap(_ => Vector(s"Could not parse record: '$rawTestRecord'"))
        }.sequence.leftMap(_.head)
      }
      testRecords.map(TestData(_))
    }.fold(_ => Left("Could not read test data"), identity)
  }

}
