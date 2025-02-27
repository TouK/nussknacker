package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.definition.test.{PreliminaryScenarioTestData, PreliminaryScenarioTestRecord}
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import pl.touk.nussknacker.ui.api.TestDataSettings
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioTestDataSerDe.{DeserializationError, SerializationError}

import scala.util.parsing.input.NoPosition.longString
import scala.util.parsing.json.JSON

class PreliminaryScenarioTestDataSerDeTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  private val maxSamplesCount   = 5
  private val testDataMaxLength = 1000

  private val serDe = new PreliminaryScenarioTestDataSerDe(
    TestDataSettings(
      maxSamplesCount = maxSamplesCount,
      testDataMaxLength = testDataMaxLength,
      resultsMaxBytes = Long.MaxValue
    )
  )

  private val scenarioTestData = PreliminaryScenarioTestData(
    NonEmptyList(
      PreliminaryScenarioTestRecord.Standard(
        "source1",
        Json.obj("f1" -> Json.fromString("field value"), "f2" -> Json.fromLong(42L)),
        timestamp = Some(24L)
      ),
      PreliminaryScenarioTestRecord.Simplified(Json.fromString("a JSON string")) :: Nil
    )
  )

  private val rawStringScenarioTestData =
    """{"sourceId":"source1","record":{"f1":"field value","f2":42},"timestamp":24}
      |"a JSON string"""".stripMargin

  test("should serialize scenario test data") {
    val rawScenarioTestData = serDe.serialize(scenarioTestData).rightValue

    rawScenarioTestData.content shouldBe rawStringScenarioTestData
  }

  test("should fail trying to serialize too much bytes") {
    val testData = PreliminaryScenarioTestData(
      NonEmptyList.fromListUnsafe(
        List.fill(10)(
          PreliminaryScenarioTestRecord.Standard("source1", Json.fromString("a long JSON string...".repeat(10)))
        )
      )
    )

    val error = serDe.serialize(testData).leftValue

    error shouldBe SerializationError.TooManyCharactersGenerated(length = 2449, limit = testDataMaxLength)
  }

  test("should deserialize scenario test data") {
    val result = serDe.deserialize(RawScenarioTestData(rawStringScenarioTestData)).rightValue

    result shouldBe scenarioTestData
  }

  test("should fail trying to parse too many characters") {
    val longString = "a long JSON string...".repeat(10)
    val tooBigRawScenarioTestData =
      RawScenarioTestData(List.fill(10)(s"""{"sourceId":"source1","record":"$longString"}""").mkString("\n"))

    val error = serDe.deserialize(tooBigRawScenarioTestData).leftValue

    error shouldBe DeserializationError.TooManyCharacters(length = 2449, limit = testDataMaxLength)
  }

  test("should fail trying to parse too many records") {
    val tooBigRawScenarioTestData =
      RawScenarioTestData(List.fill(10)("""{"sourceId":"source1","record":"a JSON string"}""").mkString("\n"))

    val error = serDe.deserialize(tooBigRawScenarioTestData).leftValue

    error shouldBe DeserializationError.TooManySamples(size = 10, limit = maxSamplesCount)
  }

  test("should fail trying to parse invalid record") {
    val invalidRecord = "not a test record"

    val error = serDe.deserialize(RawScenarioTestData(invalidRecord)).leftValue

    error shouldBe DeserializationError.RecordParsingError(invalidRecord)
  }

}
