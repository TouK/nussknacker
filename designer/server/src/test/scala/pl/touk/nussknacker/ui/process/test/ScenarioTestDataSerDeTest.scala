package pl.touk.nussknacker.ui.process.test

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import pl.touk.nussknacker.ui.api.TestDataSettings

import java.nio.charset.StandardCharsets

class ScenarioTestDataSerDeTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  private val maxSamplesCount = 5
  private val testDataMaxBytes = 100
  private val scenarioTestDataSerDe = new ScenarioTestDataSerDe(TestDataSettings(maxSamplesCount = maxSamplesCount, testDataMaxBytes = testDataMaxBytes, resultsMaxBytes = Int.MaxValue))

  private val scenarioTestData = TestData(List(
    TestRecord(Json.obj("f1" -> Json.fromString("field value"), "f2" -> Json.fromLong(42L))),
    TestRecord(Json.fromString("a JSON string")),
  ))
  private val rawStringScenarioTestData =
    """{"f1":"field value","f2":42}
      |"a JSON string"""".stripMargin

  test("should serialize scenario test data") {
    val rawScenarioTestData = scenarioTestDataSerDe.serializeTestData(scenarioTestData).rightValue

    new String(rawScenarioTestData.content, StandardCharsets.UTF_8) shouldBe rawStringScenarioTestData
  }

  test("should fail trying to serialize too much bytes") {
    val testData = TestData(List.fill(10)(TestRecord(Json.fromString("a JSON string"))))

    val error = scenarioTestDataSerDe.serializeTestData(testData).leftValue

    error shouldBe s"Too much data generated, limit is: $testDataMaxBytes"
  }

  test("should prepare scenario test data") {
    val result = scenarioTestDataSerDe.prepareTestData(RawScenarioTestData(rawStringScenarioTestData.getBytes(StandardCharsets.UTF_8))).rightValue

    result shouldBe scenarioTestData
  }

  test("should fail trying to parse too many records") {
    val tooBigRawScenarioTestData = RawScenarioTestData(List.fill(10)("\"a JSON string\"").mkString("\n").getBytes(StandardCharsets.UTF_8))

    val error = scenarioTestDataSerDe.prepareTestData(tooBigRawScenarioTestData).leftValue

    error shouldBe s"Too many samples: 10, limit is: $maxSamplesCount"
  }

  test("should fail trying to parse invalid record") {
    val invalidRecord = "not a test record"

    val error = scenarioTestDataSerDe.prepareTestData(RawScenarioTestData(invalidRecord.getBytes(StandardCharsets.UTF_8))).leftValue

    error shouldBe s"Could not parse record: '$invalidRecord'"
  }
}
