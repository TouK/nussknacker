package pl.touk.nussknacker.ui.process.test

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.definition.test.{PreliminaryScenarioTestData, PreliminaryScenarioTestRecord}
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import pl.touk.nussknacker.ui.api.TestDataSettings

class PreliminaryScenarioTestDataSerDeTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  private val maxSamplesCount = 5
  private val testDataMaxLength = 200
  private val serDe = new PreliminaryScenarioTestDataSerDe(TestDataSettings(maxSamplesCount = maxSamplesCount, testDataMaxLength = testDataMaxLength, resultsMaxBytes = Int.MaxValue))

  private val scenarioTestData = PreliminaryScenarioTestData(List(
    PreliminaryScenarioTestRecord.Standard("source1", Json.obj("f1" -> Json.fromString("field value"), "f2" -> Json.fromLong(42L)), timestamp = Some(24L)),
    PreliminaryScenarioTestRecord.Simplified(Json.fromString("a JSON string")),
  ))
  private val rawStringScenarioTestData =
    """{"sourceId":"source1","record":{"f1":"field value","f2":42},"timestamp":24}
      |"a JSON string"""".stripMargin

  test("should serialize scenario test data") {
    val rawScenarioTestData = serDe.serialize(scenarioTestData).rightValue

    rawScenarioTestData.content shouldBe rawStringScenarioTestData
  }

  test("should fail trying to serialize too much bytes") {
    val testData = PreliminaryScenarioTestData(List.fill(10)(PreliminaryScenarioTestRecord.Standard("source1", Json.fromString("a JSON string"))))

    val error = serDe.serialize(testData).leftValue

    error shouldBe s"Too much data generated, limit is: $testDataMaxLength"
  }

  test("should deserialize scenario test data") {
    val result = serDe.deserialize(RawScenarioTestData(rawStringScenarioTestData)).rightValue

    result shouldBe scenarioTestData
  }

  test("should fail trying to parse too many records") {
    val tooBigRawScenarioTestData = RawScenarioTestData(List.fill(10)("""{"sourceId":"source1","record":"a JSON string"}""").mkString("\n"))

    val error = serDe.deserialize(tooBigRawScenarioTestData).leftValue

    error shouldBe s"Too many samples: 10, limit is: $maxSamplesCount"
  }

  test("should fail trying to parse invalid record") {
    val invalidRecord = "not a test record"

    val error = serDe.deserialize(RawScenarioTestData(invalidRecord)).leftValue

    error shouldBe s"Could not parse record: '$invalidRecord'"
  }
}
