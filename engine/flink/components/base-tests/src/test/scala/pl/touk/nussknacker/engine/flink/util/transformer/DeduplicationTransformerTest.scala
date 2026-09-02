package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInfo
import org.apache.flink.configuration.Configuration
import org.apache.flink.metrics.Counter
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{ProcessName, SourceFactory}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.StandardTimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass.CaseClassTypeInfoFactory
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.functional.{TestReporter, TestReporterUtil}
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class DeduplicationTransformerTest
    extends AnyFunSuite
    with FlinkSpec
    with Matchers
    with OptionValues
    with ValidatedValuesDetailedMessage {

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExtraComponents(
      List(ComponentDefinition("deduplication", DeduplicationTransformer))
    )
    .build()

  private val reporterName = getClass.getName

  override protected def prepareFlinkConfiguration(): Configuration =
    TestReporterUtil.configWithTestMetrics(reporterName, super.prepareFlinkConfiguration())

  override protected def afterAll(): Unit = {
    TestReporter.remove(reporterName)
    super.afterAll()
  }

  private val baseTimestamp = 1_000_000L

  private def ts(minutesOffset: Int): Long =
    baseTimestamp + minutesOffset * 60_000L

  private def deduplicationScenario(
      name: String,
      groupBy: String = "#input.key",
      value: String = "#input",
      filterCondition: String = "#incomingEntry.value.amount >= #previousEntry.value.amount + 20",
      ttl: String = "T(java.time.Duration).parse('PT1H')",
      timeMode: String = "EventTime",
  ): CanonicalProcess = {
    val params: List[(String, Expression)] =
      List(
        "groupBy"         -> groupBy.spel,
        "value"           -> value.spel,
        "filterCondition" -> filterCondition.spel,
        "ttl"             -> ttl.spel,
        "timeMode"        -> s"'$timeMode'".spel
      )

    ScenarioBuilder
      .streaming(getClass.getName + "-" + name)
      .source("start", TestScenarioRunner.testDataSource)
      .customNodeNoOutput("deduplication", "deduplication", params: _*)
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#input.amount".spel)
  }

  private def bothOutputsScenario(scenarioName: ProcessName): CanonicalProcess =
    ScenarioBuilder
      .streaming(scenarioName.value)
      .source("start", TestScenarioRunner.testDataSource)
      .customNodeWithOutputs(
        "deduplication",
        None,
        "deduplication",
        List(
          "passed" -> GraphBuilder
            .emptySink("main-sink", TestScenarioRunner.testResultSink, "value" -> "'main:' + #input.amount".spel)
            .value,
          "rejected" -> GraphBuilder
            .emptySink(
              "rejected-sink",
              TestScenarioRunner.testResultSink,
              "value" -> "'rejected:' + #input.amount".spel
            )
            .value
        ),
        "groupBy"         -> "#input.key".spel,
        "value"           -> "#input".spel,
        "filterCondition" -> "false".spel,
        "ttl"             -> "T(java.time.Duration).parse('PT1H')".spel,
        "timeMode"        -> "'EventTime'".spel
      )

  // One key, so with a false filter condition the first record is accepted and the two others are rejected.
  private val threeRecordsOfOneKey = List(
    DeduplicationTestRecord("sub1", 0, ts(0)),
    DeduplicationTestRecord("sub1", 5, ts(1)),
    DeduplicationTestRecord("sub1", 10, ts(2)),
  )

  private val watermarkStrategy =
    StandardTimestampWatermarkHandler.afterEachEvent[DeduplicationTestRecord](
      (record: DeduplicationTestRecord, _: Long) => record.eventTimestamp
    )

  private def runScenario(scenario: CanonicalProcess, data: List[DeduplicationTestRecord]) =
    runner.runWithData[DeduplicationTestRecord, Int](scenario, data, timestampAssigner = Some(watermarkStrategy))

  // Summed rather than taken as the lone element: one counter is registered per subtask of the operator.
  private def nodeCount(nodeId: String)(implicit scenarioName: ProcessName): Long = {
    val reporter = TestReporter.get(reporterName)
    val counters = reporter.testMetrics[Counter](s"nodeId.$nodeId.nodeCount")
    withClue(s"no nodeCount metric for '$nodeId', registered: ${reporter.namedMetricsForScenario.keys.toList.sorted}") {
      counters should not be empty
    }
    counters.map(_.getCount).sum
  }

  // These names are the component's contract, not an implementation detail: the designer labels the node's ports
  // with them and a scenario's edge refers to the additional one by name, so renaming either breaks stored scenarios.
  test("should declare passed as its main output and rejected as the additional one") {
    DeduplicationTransformer.outputs.map(_.name) shouldBe NonEmptyList.of("passed", "rejected")
  }

  // The scenario diagram's counts are read from these metrics, so they pin what a user sees on a deduplication node -
  // see docs/components/deduplication.mdx. The node's own count is everything that reached it, not just the accepted
  // records; only the branches below it tell the two apart.
  test("should route duplicates to the rejected output with unchanged input context, and count both branches") {
    implicit val scenarioName: ProcessName = ProcessName(getClass.getName + "-both-outputs")

    val result = runner.runWithData[DeduplicationTestRecord, String](
      bothOutputsScenario(scenarioName),
      threeRecordsOfOneKey,
      timestampAssigner = Some(watermarkStrategy)
    )

    result.validValue.successes.sorted shouldBe List("main:0", "rejected:10", "rejected:5")

    nodeCount("start") shouldBe 3L
    nodeCount("deduplication") shouldBe 3L
    nodeCount("main-sink") shouldBe 1L
    nodeCount("rejected-sink") shouldBe 2L
  }

  test("should emit first record and next when condition is met") {
    val data = List(
      DeduplicationTestRecord("sub1", 0, ts(0)),
      DeduplicationTestRecord("sub1", 18, ts(1)),
      DeduplicationTestRecord("sub1", 19, ts(2)),
      DeduplicationTestRecord("sub1", 20, ts(3)),
      DeduplicationTestRecord("sub1", 38, ts(4)),
      DeduplicationTestRecord("sub1", 39, ts(5)),
      DeduplicationTestRecord("sub1", 40, ts(6)),
    )

    val result = runScenario(deduplicationScenario("condition"), data)

    result.validValue.successes shouldBe List(0, 20, 40)
  }

  test("should deduplicate all events in single group when key is empty string") {
    val data = List(
      DeduplicationTestRecord("sub1", 0, ts(0)),
      DeduplicationTestRecord("sub2", 18, ts(1)),
      DeduplicationTestRecord("sub1", 20, ts(2)),
    )

    val result = runScenario(deduplicationScenario("default-key", groupBy = "''"), data)

    result.validValue.successes shouldBe List(0, 20)
  }

  test("should report error when groupBy evaluates to null") {
    val data = List(
      DeduplicationTestRecord("sub1", 0, ts(0)),
    )

    val result = runScenario(deduplicationScenario("null-groupby", groupBy = "null"), data)

    val errors = result.validValue.errors
    errors should not be empty
    errors.head.throwable.getMessage should include("key value derived from parameter 'groupBy' evaluated to null")
  }

  // Same records as with `rejected` wired above, so the counts also show that leaving an output unconnected registers
  // no downstream interpretation and does not reach back into the node's own count.
  test("should emit only first event per key when filter condition is false, and count with rejected unwired") {
    implicit val scenarioName: ProcessName = ProcessName(getClass.getName + "-simple-dedup")

    val result = runScenario(deduplicationScenario("simple-dedup", filterCondition = "false"), threeRecordsOfOneKey)

    result.validValue.successes shouldBe List(0)

    nodeCount("start") shouldBe 3L
    nodeCount("deduplication") shouldBe 3L
    nodeCount("end") shouldBe 1L
  }

  test("should treat event as new after a TTL gap without an artificial watermark carrier") {
    val data = List(
      DeduplicationTestRecord("sub1", 10, ts(0)),  // first event -> emitted
      DeduplicationTestRecord("sub1", 20, ts(1)),  // within TTL -> deduplicated
      DeduplicationTestRecord("sub1", 30, ts(70)), // gap 70min > TTL 1h -> treated as new -> emitted
      DeduplicationTestRecord("sub1", 40, ts(71)), // within TTL again -> deduplicated
    )

    val result = runScenario(deduplicationScenario("ttl-gap", value = "#input.amount", filterCondition = "false"), data)

    result.validValue.successes shouldBe List(10, 30)
  }

  test("should emit all events when filter condition is always true") {
    val result = runScenario(deduplicationScenario("always-true", filterCondition = "true"), threeRecordsOfOneKey)

    result.validValue.successes shouldBe List(0, 5, 10)
  }

  test("should handle errors in value expression evaluation gracefully") {
    val data = List(
      DeduplicationTestRecord("sub1", 10, ts(0)),
      DeduplicationTestRecord("sub1", 0, ts(1)),
      DeduplicationTestRecord("sub1", 20, ts(2)),
    )

    val result =
      runScenario(deduplicationScenario("error-handling", value = "1 / #input.amount", filterCondition = "true"), data)

    val validResult = result.validValue
    validResult.successes shouldBe List(10, 20)
    val errors = validResult.errors
    errors should have size 1
    errors.head.nodeId shouldBe Some("deduplication")
    errors.head.throwable.getMessage should include("/ by zero")
  }

  test("should pass at most N messages per window using #passedEventsCount and reset after a TTL gap") {
    val data = List(
      DeduplicationTestRecord("sub1", 1, ts(0)),  // first of window -> emitted (passed -> 1)
      DeduplicationTestRecord("sub1", 2, ts(1)),  // passedEventsCount=1 < 2 -> emitted (passed -> 2)
      DeduplicationTestRecord("sub1", 3, ts(2)),  // passedEventsCount=2 < 2 is false -> deduplicated
      DeduplicationTestRecord("sub1", 4, ts(3)),  // still blocked
      DeduplicationTestRecord("sub1", 5, ts(70)), // gap 70min > TTL 1h -> new window -> emitted
    )

    val result = runScenario(
      deduplicationScenario("passed-count-cap", value = "#input.amount", filterCondition = "#passedEventsCount < 2"),
      data
    )

    result.validValue.successes shouldBe List(1, 2, 5)
  }

  test("should sample arrivals within a window using #eventsCount") {
    val data = List(
      DeduplicationTestRecord("sub1", 1, ts(0)), // first of window -> emitted (eventsCount 0 -> 1)
      DeduplicationTestRecord("sub1", 2, ts(1)), // eventsCount=1 -> 1 % 2 != 0 -> deduplicated
      DeduplicationTestRecord("sub1", 3, ts(2)), // eventsCount=2 -> 2 % 2 == 0 -> emitted
      DeduplicationTestRecord("sub1", 4, ts(3)), // eventsCount=3 -> deduplicated
      DeduplicationTestRecord("sub1", 5, ts(4)), // eventsCount=4 -> emitted
    )

    val result = runScenario(
      deduplicationScenario("events-count-sample", value = "#input.amount", filterCondition = "#eventsCount % 2 == 0"),
      data
    )

    result.validValue.successes shouldBe List(1, 3, 5)
  }

}

object DeduplicationTestRecord {
  class TypeInfoFactory extends CaseClassTypeInfoFactory[DeduplicationTestRecord]
}

@TypeInfo(classOf[DeduplicationTestRecord.TypeInfoFactory])
case class DeduplicationTestRecord(
    key: String,
    amount: Int,
    eventTimestamp: Long
)
