package pl.touk.nussknacker.ui.process.test

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestRecord}
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import pl.touk.nussknacker.ui.api.TestDataSettings

import java.nio.charset.StandardCharsets

class ScenarioTestDataSerDeTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  private val maxSamplesCount = 5
  private val testDataMaxLength = 200
  private val scenarioTestDataSerDe = new ScenarioTestDataSerDe(TestDataSettings(maxSamplesCount = maxSamplesCount, testDataMaxLength = testDataMaxLength, resultsMaxBytes = Int.MaxValue))

  private val scenarioTestData = ScenarioTestData(List(
    ScenarioTestRecord("source1", Json.obj("f1" -> Json.fromString("field value"), "f2" -> Json.fromLong(42L)), timestamp = Some(24L)),
    ScenarioTestRecord("source2", Json.fromString("a JSON string")),
  ))
  private val rawStringScenarioTestData =
    """{"sourceId":"source1","record":{"f1":"field value","f2":42},"timestamp":24}
      |{"sourceId":"source2","record":"a JSON string"}""".stripMargin

  test("should serialize scenario test data") {
    val rawScenarioTestData = scenarioTestDataSerDe.serializeTestData(scenarioTestData).rightValue

    rawScenarioTestData.content shouldBe rawStringScenarioTestData
  }

  test("should fail trying to serialize too much bytes") {
    val testData = ScenarioTestData(List.fill(10)(ScenarioTestRecord("source1", Json.fromString("a JSON string"))))

    val error = scenarioTestDataSerDe.serializeTestData(testData).leftValue

    error shouldBe s"Too much data generated, limit is: $testDataMaxLength"
  }

  test("should prepare scenario test data") {
    val result = scenarioTestDataSerDe.prepareTestData(RawScenarioTestData(rawStringScenarioTestData)).rightValue

    result shouldBe scenarioTestData
  }

  test("should fail trying to parse too many records") {
    val tooBigRawScenarioTestData = RawScenarioTestData(List.fill(10)("""{"sourceId":"source1","record":"a JSON string"}""").mkString("\n"))

    val error = scenarioTestDataSerDe.prepareTestData(tooBigRawScenarioTestData).leftValue

    error shouldBe s"Too many samples: 10, limit is: $maxSamplesCount"
  }

  test("should fail trying to parse invalid record") {
    val invalidRecord = "not a test record"

    val error = scenarioTestDataSerDe.prepareTestData(RawScenarioTestData(invalidRecord)).leftValue

    error shouldBe s"Could not parse record: '$invalidRecord'"
  }
}
