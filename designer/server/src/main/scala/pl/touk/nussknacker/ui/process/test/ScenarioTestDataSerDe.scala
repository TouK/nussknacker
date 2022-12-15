package pl.touk.nussknacker.ui.process.test

import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using
import io.circe.parser

// TODO multiple-sources-test: tests; combine with TestInfoProvider?; switch TestData to ScenarioTestData
object ScenarioTestDataSerDe {

  def serializeTestData(testData: TestData, testDataMaxBytes: Int): Either[String, RawTestData] = {
    val content = testData.testRecords
      .map(_.json.noSpaces)
      .mkString("\n")
      .getBytes(StandardCharsets.UTF_8)
    Either.cond(content.size <= testDataMaxBytes, RawTestData(content), s"Too much data generated, limit is: $testDataMaxBytes")
  }

  def prepareTestData(rawTestData: RawTestData, maxSamplesCount: Int): Either[String, TestData] = {
    import cats.syntax.either._
    import cats.syntax.traverse._
    import cats.implicits.catsStdInstancesForEither

    Using(Source.fromBytes(rawTestData.content, StandardCharsets.UTF_8.name())) { source =>
      val rawTestRecords = source
        .getLines()
        .toList
      val limitedRawTestRecords = Either.cond(rawTestRecords.size <= maxSamplesCount,
        rawTestRecords,
        s"Too many samples: ${rawTestRecords.size}, limit is: $maxSamplesCount")
      val testRecords: Either[String, List[TestRecord]] = limitedRawTestRecords.flatMap { rawTestRecords =>
        rawTestRecords
          .map(parser.parse)
          .map(_.map(TestRecord))
          .map(_.leftMap(_ => Vector("Could not parse sample")))
          .sequence
          .leftMap(_.head)
      }
      testRecords.map(TestData(_))
    }.fold(_ => Left("Could not read test data"), identity)
  }

}
