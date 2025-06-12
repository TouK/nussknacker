package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import pl.touk.nussknacker.ui.api.TestDataSettings
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioRecordsSerDe.{DeserializationError, SerializationError}

class PreliminaryScenarioRecordsSerDeTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  private val testDataMaxLength = 1000
  private val maxSamplesCount   = 5

  private val serDe = new PreliminaryScenarioRecordsSerDe(
    serializedContentMaxLength = Some(testDataMaxLength),
    maxRecordsCount = Some(maxSamplesCount),
  )

  private val testDataRecord = PreliminaryScenarioRecord(
    "source1",
    Json.obj("f1" -> Json.fromString("field value"), "f2" -> Json.fromLong(42L)),
    timestamp = Some(24L)
  )

  private val scenarioTestData = PreliminaryScenarioRecords(
    NonEmptyList.one(testDataRecord)
  )

  private val rawStringScenarioTestData =
    """{"sourceId":"source1","record":{"f1":"field value","f2":42},"timestamp":24}""".stripMargin

  test("should serialize scenario test data") {
    val rawScenarioTestData = serDe.serialize(scenarioTestData).rightValue

    rawScenarioTestData.content shouldBe rawStringScenarioTestData
  }

  test("should fail trying to serialize too much bytes") {
    val testData = PreliminaryScenarioRecords(
      NonEmptyList.fromListUnsafe(
        List.fill(30)(testDataRecord)
      )
    )

    val error = serDe.serialize(testData).leftValue

    error shouldBe SerializationError.TooManyCharactersGenerated(length = 2279, limit = testDataMaxLength)
  }

  test("should deserialize scenario test data") {
    val result = serDe.deserialize(SerializedScenarioRecordsContent(rawStringScenarioTestData)).rightValue

    result shouldBe scenarioTestData
  }

  test("should fail trying to parse too many characters") {
    val longString = "a long JSON string...".repeat(10)
    val tooBigRawScenarioTestData =
      SerializedScenarioRecordsContent(
        List.fill(10)(s"""{"sourceId":"source1","record":"$longString"}""").mkString("\n")
      )

    val error = serDe.deserialize(tooBigRawScenarioTestData).leftValue

    error shouldBe DeserializationError.TooManyCharacters(length = 2449, limit = testDataMaxLength)
  }

  test("should fail trying to parse too many records") {
    val tooBigRawScenarioTestData =
      SerializedScenarioRecordsContent(
        List.fill(10)("""{"sourceId":"source1","record":"a JSON string"}""").mkString("\n")
      )

    val error = serDe.deserialize(tooBigRawScenarioTestData).leftValue

    error shouldBe DeserializationError.TooManyRecords(size = 10, limit = maxSamplesCount)
  }

  test("should fail trying to parse invalid record") {
    val invalidRecord = "not a test record"
    val scenarioTestData =
      s"""$rawStringScenarioTestData
        |$invalidRecord""".stripMargin

    val error = serDe.deserialize(SerializedScenarioRecordsContent(scenarioTestData)).leftValue

    error shouldBe DeserializationError.RecordParsingError(invalidRecord, recordIndex = 1)
  }

}
