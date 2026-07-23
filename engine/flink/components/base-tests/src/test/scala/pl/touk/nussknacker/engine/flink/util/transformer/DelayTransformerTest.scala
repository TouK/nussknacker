package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInfo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.InvalidDurationParameter
import pl.touk.nussknacker.engine.api.definition.NonNegativeDurationValidation
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.api.TimeMode
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.StandardTimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass.CaseClassTypeInfoFactory
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class DelayTransformerTest extends AnyFunSuite with FlinkSpec with Matchers with ValidatedValuesDetailedMessage {

  private val watermarkStrategy =
    StandardTimestampWatermarkHandler.afterEachEvent[DelayTestRecord]((record: DelayTestRecord, _: Long) =>
      record.eventTimestamp
    )

  test("should release queued events for all keys in event time after watermark passes delay") {
    val scenarioName: ProcessName = ProcessName(getClass.getName + "-event-time")
    val scenario =
      createScenario(scenarioName, TestScenarioRunner.testDataSource, delay = "PT1M", timeMode = TimeMode.EventTime)

    val data = List(
      DelayTestRecord("A", 1, 0L),
      DelayTestRecord("B", 2, 0L),
      DelayTestRecord("A", 3, 0L),
      DelayTestRecord("B", 4, 0L),
    )

    val runner = TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
      .build()

    val result = runner.runWithData[DelayTestRecord, Int](scenario, data, timestampAssigner = Some(watermarkStrategy))
    result.validValue.successes should contain theSameElementsAs List(1, 2, 3, 4)
  }

  test("should flush queued events for all keys in processing time after bounded input ends") {
    val scenarioName: ProcessName = ProcessName(getClass.getName + "-processing-time")
    val scenario = createScenario(
      scenarioName,
      TestScenarioRunner.testDataSource,
      delay = "PT2S",
      timeMode = TimeMode.ProcessingTime
    )

    val data = List(
      DelayTestRecord("A", 1, 0L),
      DelayTestRecord("B", 2, 0L),
      DelayTestRecord("A", 3, 0L),
      DelayTestRecord("B", 4, 0L),
    )

    val runner = TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
      .build()

    val result = runner.runWithData[DelayTestRecord, Int](scenario, data)
    result.validValue.successes should contain theSameElementsAs List(1, 2, 3, 4)
  }

  test("should compute the delay per event from an input field") {
    implicit val scenarioName: ProcessName = ProcessName(getClass.getName + "-per-event-delay")
    val scenario = createScenarioWithDelayExpression(
      scenarioName,
      TestScenarioRunner.testDataSource,
      delayExpression = "T(java.time.Duration).ofMillis(#input.delayMillis)",
      timeMode = TimeMode.EventTime
    )

    // each record carries its own delay; the bounded final watermark releases them all regardless of size
    val data = List(
      DelayTestRecord("A", 1, 0L, 1000L),
      DelayTestRecord("B", 2, 0L, 60000L),
      DelayTestRecord("A", 3, 0L, 0L),
      DelayTestRecord("B", 4, 0L, 120000L),
    )

    val runner = TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
      .build()

    val result = runner.runWithData[DelayTestRecord, Int](scenario, data, timestampAssigner = Some(watermarkStrategy))
    result.validValue.successes should contain theSameElementsAs List(1, 2, 3, 4)
    result.validValue.errors shouldBe empty
  }

  test("should treat a per-event negative delay as no delay and emit the event without error") {
    implicit val scenarioName: ProcessName = ProcessName(getClass.getName + "-negative-delay")
    val scenario = createScenarioWithDelayExpression(
      scenarioName,
      TestScenarioRunner.testDataSource,
      delayExpression = "T(java.time.Duration).ofMillis(#input.delayMillis)",
      timeMode = TimeMode.EventTime
    )

    val data = List(
      DelayTestRecord("A", 1, 0L, 0L),
      DelayTestRecord("B", 2, 0L, -1L), // -> negative delay -> released immediately, no error
      DelayTestRecord("A", 3, 0L, 1000L),
    )

    val runner = TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
      .build()

    val result = runner.runWithData[DelayTestRecord, Int](scenario, data, timestampAssigner = Some(watermarkStrategy))
    result.validValue.successes should contain theSameElementsAs List(1, 2, 3)
    result.validValue.errors shouldBe empty
  }

  test("should reject a constant negative delay at compile time") {
    implicit val scenarioName: ProcessName = ProcessName(getClass.getName + "-constant-negative-delay")
    val scenario =
      createScenario(scenarioName, TestScenarioRunner.testDataSource, delay = "PT-1S", timeMode = TimeMode.EventTime)

    val runner = TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
      .build()

    val result = runner.runWithData[DelayTestRecord, Int](scenario, List.empty)
    result.invalidValue should matchPattern {
      case NonEmptyList(
            InvalidDurationParameter(
              NonNegativeDurationValidation.Message,
              NonNegativeDurationValidation.Description,
              DelayTransformer.delayParamName,
              "delay"
            ),
            Nil
          ) =>
    }
  }

  private def createScenario(
      processName: ProcessName,
      sourceComponentId: String,
      delay: String,
      timeMode: TimeMode
  ): CanonicalProcess =
    createScenarioWithDelayExpression(
      processName,
      sourceComponentId,
      delayExpression = s"T(java.time.Duration).parse('$delay')",
      timeMode = timeMode
    )

  private def createScenarioWithDelayExpression(
      processName: ProcessName,
      sourceComponentId: String,
      delayExpression: String,
      timeMode: TimeMode
  ): CanonicalProcess =
    ScenarioBuilder
      .streaming(processName.value)
      .parallelism(1)
      .source("start", sourceComponentId)
      .customNodeNoOutput(
        "delay",
        "delay",
        "keyBy"    -> "#input.key".spel,
        "delay"    -> delayExpression.spel,
        "timeMode" -> s"'$timeMode'".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#input.value".spel)

}

object DelayTestRecord {
  class TypeInfoFactory extends CaseClassTypeInfoFactory[DelayTestRecord]
}

@TypeInfo(classOf[DelayTestRecord.TypeInfoFactory])
case class DelayTestRecord(key: String, value: Int, eventTimestamp: Long, delayMillis: Long = 0L)
