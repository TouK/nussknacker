package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInfo
import org.apache.flink.configuration.Configuration
import org.apache.flink.metrics.{Gauge => FlinkGauge}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.InvalidDurationParameter
import pl.touk.nussknacker.engine.api.definition.NonNegativeDurationValidation
import pl.touk.nussknacker.engine.api.exception.ParameterRuntimeValidationException
import pl.touk.nussknacker.engine.api.process.{ProcessName, SourceFactory}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.api.TimeMode
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.WatermarkStrategyUtils
import pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass.CaseClassTypeInfoFactory
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.source.BlockingQueueSource
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
import pl.touk.nussknacker.engine.process.functional.{TestReporter, TestReporterUtil}
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.{ValidatedValuesDetailedMessage, VeryPatientScalaFutures}

import java.time.Duration

class DelayTransformerTest
    extends AnyFunSuite
    with FlinkSpec
    with Matchers
    with ValidatedValuesDetailedMessage
    with VeryPatientScalaFutures {

  private val reporterName = getClass.getName

  override protected def prepareFlinkConfiguration(): Configuration =
    TestReporterUtil.configWithTestMetrics(reporterName)

  override protected def afterAll(): Unit = {
    TestReporter.remove(reporterName)
    super.afterAll()
  }

  private val watermarkStrategy =
    WatermarkStrategyUtils.afterEachEvent[DelayTestRecord]((record: DelayTestRecord, _: Long) => record.eventTimestamp)

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .build()

  // The two holding tests use withBlockingDelayScenario: a BlockingQueueSource driven via a detached run, so they can
  // observe the intermediate state and assert that events are actually *held* until the delay passes. Bounded
  // runWithData cannot show this, because the final watermark always releases everything regardless of the delay
  // (so a delay of zero would still pass those tests).
  test("should hold events in event time until the watermark passes the delay period") {
    implicit val scenarioName: ProcessName = ProcessName(getClass.getName + "-holding")
    withBlockingDelayScenario(scenarioName, delay = "PT1M", timeMode = TimeMode.EventTime, _.eventTimestamp) {
      (source, fixture) =>
        // event at t=0, watermark advances to 0, timer scheduled at 60000 - not yet fired
        source.add(DelayTestRecord("key", 1, 0L))

        // nothing released yet, but the event is buffered, so bufferedEvents reflects it
        eventually {
          fixture.testSinkResults shouldBe empty
          gaugeValue("bufferedEvents") shouldBe 1L
        }

        // event at t=30000, watermark advances to 30000 - still before 60000
        source.add(DelayTestRecord("key", 2, 30000L))
        eventually {
          fixture.testSinkResults shouldBe empty
          gaugeValue("bufferedEvents") shouldBe 2L
        }

        // event at t=60001, watermark advances to 60001 - the first event's timer fires
        source.add(DelayTestRecord("key", 3, 60001L))
        eventually {
          fixture.testSinkResults should have size 1
          gaugeValue("bufferedEvents") shouldBe 2L
        }

        // event at t=120002, watermark advances past the remaining timers (90000 and 120001)
        source.add(DelayTestRecord("key", 4, 120002L))
        eventually {
          fixture.testSinkResults should have size 2
          gaugeValue("bufferedEvents") shouldBe 1L
        }
    }
  }

  // Processing time is measured by the wall clock, so this test asserts that the event is held for the delay
  // duration and only released once it elapses (via the natural processing-time timer, not the end-of-input flush).
  test("should hold events in processing time until the delay elapses") {
    implicit val scenarioName: ProcessName = ProcessName(getClass.getName + "-holding-processing-time")

    withBlockingDelayScenario(scenarioName, delay = "PT1S", timeMode = TimeMode.ProcessingTime) { (source, fixture) =>
      source.add(DelayTestRecord("key", 1, 0L))
      // shortly after adding, before the 1s delay elapses, the event is still held
      Thread.sleep(500)
      fixture.testSinkResults shouldBe empty
      gaugeValue("bufferedEvents") shouldBe 1L

      // once the processing-time delay elapses the timer fires and the event is released
      Thread.sleep(800)
      fixture.testSinkResults should have size 1
      gaugeValue("bufferedEvents") shouldBe 0L
    }
  }

  test("should release queued events for all keys in event time after watermark passes delay") {
    implicit val scenarioName: ProcessName = ProcessName(getClass.getName + "-event-time")
    val scenario =
      createScenario(scenarioName, TestScenarioRunner.testDataSource, delay = "PT1M", timeMode = TimeMode.EventTime)

    val data = List(
      DelayTestRecord("A", 1, 0L),
      DelayTestRecord("B", 2, 0L),
      DelayTestRecord("A", 3, 0L),
      DelayTestRecord("B", 4, 0L),
    )

    val result = runner.runWithData[DelayTestRecord, Int](scenario, data, watermarkStrategy = Some(watermarkStrategy))
    result.validValue.successes should contain theSameElementsAs List(1, 2, 3, 4)

    // the final watermark releases every buffered event, so all are emitted and nothing remains queued
    gaugeValue("bufferedEvents") shouldBe 0L
  }

  test("should flush queued events for all keys in processing time after bounded input ends") {
    implicit val scenarioName: ProcessName = ProcessName(getClass.getName + "-processing-time")
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

    val result = runner.runWithData[DelayTestRecord, Int](scenario, data)
    result.validValue.successes should contain theSameElementsAs List(1, 2, 3, 4)

    // after the bounded input ends every buffered event is flushed, so all are emitted and nothing remains queued
    gaugeValue("bufferedEvents") shouldBe 0L
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

    val result = runner.runWithData[DelayTestRecord, Int](scenario, data, watermarkStrategy = Some(watermarkStrategy))
    result.validValue.successes should contain theSameElementsAs List(1, 2, 3, 4)
    result.validValue.errors shouldBe empty
  }

  test("should route a per-event null delay to the error handler and keep processing the rest") {
    implicit val scenarioName: ProcessName = ProcessName(getClass.getName + "-null-delay")
    val scenario = createScenarioWithDelayExpression(
      scenarioName,
      TestScenarioRunner.testDataSource,
      // null for the offending record (delayMillis < 0 used as a "missing" marker -> null Duration)
      delayExpression = "#input.delayMillis < 0 ? null : T(java.time.Duration).ofMillis(#input.delayMillis)",
      timeMode = TimeMode.EventTime
    )

    val data = List(
      DelayTestRecord("A", 1, 0L, 0L),
      DelayTestRecord("B", 2, 0L, -1L), // -> null delay -> error
      DelayTestRecord("A", 3, 0L, 0L),
    )

    val result = runner.runWithData[DelayTestRecord, Int](scenario, data, watermarkStrategy = Some(watermarkStrategy))
    result.validValue.successes should contain theSameElementsAs List(1, 3)
    val errors = result.validValue.errors
    errors should have size 1
    errors.head.nodeId.map(_.value) shouldBe Some("delay")
    errors.head.context.variables.get("input").map(_.asInstanceOf[DelayTestRecord].value) shouldBe Some(2)
    errors.head.throwable shouldBe a[ParameterRuntimeValidationException]
    errors.head.throwable.getMessage should include("This field value can not be null")
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

    val result = runner.runWithData[DelayTestRecord, Int](scenario, data, watermarkStrategy = Some(watermarkStrategy))
    result.validValue.successes should contain theSameElementsAs List(1, 2, 3)
    result.validValue.errors shouldBe empty
  }

  test("should reject a constant negative delay at compile time") {
    implicit val scenarioName: ProcessName = ProcessName(getClass.getName + "-constant-negative-delay")
    val scenario =
      createScenario(scenarioName, TestScenarioRunner.testDataSource, delay = "PT-1S", timeMode = TimeMode.EventTime)

    val result = runner.runWithData[DelayTestRecord, Int](scenario, List.empty)
    result.invalidValue should matchPattern {
      case NonEmptyList(
            InvalidDurationParameter(
              NonNegativeDurationValidation.Message,
              NonNegativeDurationValidation.Description,
              DelayTransformer.delayParamName,
              NodeId("delay")
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

  private def withBlockingDelayScenario(
      processName: ProcessName,
      delay: String,
      timeMode: TimeMode,
      timestampExtractor: DelayTestRecord => Long = _ => 0L
  )(body: (BlockingQueueSource[DelayTestRecord], ScenarioVerificationFixture) => Unit): Unit = {
    val source = BlockingQueueSource.create[DelayTestRecord](timestampExtractor, Duration.ZERO)

    val blockingRunner = TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
      .withExtraComponents(
        List(
          ComponentDefinition("blocking-source", SourceFactory.noParamUnboundedStreamFactory[DelayTestRecord](source))
        )
      )
      .build()

    val scenario = createScenario(processName, sourceComponentId = "blocking-source", delay, timeMode)

    blockingRunner.withRunningScenario(scenario) { fixture =>
      body(source, fixture)
      source.finish()
    }
  }

  // Sum of the given gauge across all delay subtasks, i.e. the per-scenario total. Each scenario (including detached
  // ones run via withRunningScenario) executes under its own job name, so there is no cross-scenario contamination.
  // All test events share a single key, so exactly one subtask buffers/emits them and the rest report 0.
  private def gaugeValue(metricName: String)(implicit scenarioName: ProcessName): Long =
    TestReporter.get(reporterName).testMetrics[FlinkGauge[Long]](metricName).map(_.getValue).sum

}

object DelayTestRecord {
  class TypeInfoFactory extends CaseClassTypeInfoFactory[DelayTestRecord]
}

@TypeInfo(classOf[DelayTestRecord.TypeInfoFactory])
case class DelayTestRecord(key: String, value: Int, eventTimestamp: Long, delayMillis: Long = 0L)
