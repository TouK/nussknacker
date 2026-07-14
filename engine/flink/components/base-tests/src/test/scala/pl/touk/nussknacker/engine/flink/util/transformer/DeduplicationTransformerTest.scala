package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInfo
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.StandardTimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass.CaseClassTypeInfoFactory
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class DeduplicationTransformerTest
    extends AnyFunSuite
    with BeforeAndAfterAll
    with Matchers
    with ValidatedValuesDetailedMessage {

  private lazy val flinkMiniClusterWithServices =
    FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
    .withExtraComponents(
      List(ComponentDefinition("deduplication", DeduplicationTransformer))
    )
    .build()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  private val baseTimestamp = 1_000_000L

  private def ts(minutesOffset: Int): Long =
    baseTimestamp + minutesOffset * 60_000L

  private def deduplicationScenario(
      name: String,
      groupBy: String = "#input.key",
      value: String = "#input",
      filterCondition: String = "#incomingEntry.value.amount >= #previousEntry.value.amount + 20",
  ): CanonicalProcess = {
    val params: List[(String, Expression)] =
      List(
        "groupBy"         -> groupBy.spel,
        "value"           -> value.spel,
        "filterCondition" -> filterCondition.spel,
        "ttl"             -> "T(java.time.Duration).parse('PT1H')".spel
      )

    ScenarioBuilder
      .streaming(getClass.getName + "-" + name)
      .source("start", TestScenarioRunner.testDataSource)
      .customNodeNoOutput("deduplication", "deduplication", params: _*)
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#input.amount".spel)
  }

  private val watermarkStrategy =
    StandardTimestampWatermarkHandler.afterEachEvent[DeduplicationTestRecord](
      (record: DeduplicationTestRecord, _: Long) => record.eventTimestamp
    )

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

    val result = runner.runWithData[DeduplicationTestRecord, Int](
      deduplicationScenario("condition"),
      data,
      timestampAssigner = Some(watermarkStrategy)
    )

    result.validValue.successes shouldBe List(0, 20, 40)
  }

  test("should deduplicate all events in single group when key is empty string") {
    val data = List(
      DeduplicationTestRecord("sub1", 0, ts(0)),
      DeduplicationTestRecord("sub2", 18, ts(1)),
      DeduplicationTestRecord("sub1", 20, ts(2)),
    )

    val result = runner.runWithData[DeduplicationTestRecord, Int](
      deduplicationScenario("default-key", groupBy = "''"),
      data,
      timestampAssigner = Some(watermarkStrategy)
    )

    result.validValue.successes shouldBe List(0, 20)
  }

  test("should report error when groupBy evaluates to null") {
    val data = List(
      DeduplicationTestRecord("sub1", 0, ts(0)),
    )

    val result = runner.runWithData[DeduplicationTestRecord, Int](
      deduplicationScenario("null-groupby", groupBy = "null"),
      data,
      timestampAssigner = Some(watermarkStrategy)
    )

    val errors = result.validValue.errors
    errors should not be empty
    errors.head.throwable.getMessage should include("key value derived from parameter 'groupBy' evaluated to null")
  }

  test("should emit only first event per key when filter condition is false (simple deduplication)") {
    val data = List(
      DeduplicationTestRecord("sub1", 0, ts(0)),
      DeduplicationTestRecord("sub1", 5, ts(1)),
      DeduplicationTestRecord("sub1", 10, ts(2)),
    )

    val result = runner.runWithData[DeduplicationTestRecord, Int](
      deduplicationScenario("simple-dedup", filterCondition = "false"),
      data,
      timestampAssigner = Some(watermarkStrategy)
    )

    result.validValue.successes shouldBe List(0)
  }

  test("should treat event as new after a TTL gap without an artificial watermark carrier") {
    val data = List(
      DeduplicationTestRecord("sub1", 10, ts(0)),  // first event -> emitted
      DeduplicationTestRecord("sub1", 20, ts(1)),  // within TTL -> deduplicated
      DeduplicationTestRecord("sub1", 30, ts(70)), // gap 70min > TTL 1h -> treated as new -> emitted
      DeduplicationTestRecord("sub1", 40, ts(71)), // within TTL again -> deduplicated
    )

    val result = runner.runWithData[DeduplicationTestRecord, Int](
      deduplicationScenario("ttl-gap", value = "#input.amount", filterCondition = "false"),
      data,
      timestampAssigner = Some(watermarkStrategy)
    )

    result.validValue.successes shouldBe List(10, 30)
  }

  test("should emit all events when filter condition is always true") {
    val data = List(
      DeduplicationTestRecord("sub1", 0, ts(0)),
      DeduplicationTestRecord("sub1", 5, ts(1)),
      DeduplicationTestRecord("sub1", 10, ts(2)),
    )

    val result = runner.runWithData[DeduplicationTestRecord, Int](
      deduplicationScenario("always-true", filterCondition = "true"),
      data,
      timestampAssigner = Some(watermarkStrategy)
    )

    result.validValue.successes shouldBe List(0, 5, 10)
  }

  test("should handle errors in value expression evaluation gracefully") {
    val data = List(
      DeduplicationTestRecord("sub1", 10, ts(0)),
      DeduplicationTestRecord("sub1", 0, ts(1)),
      DeduplicationTestRecord("sub1", 20, ts(2)),
    )

    val result = runner.runWithData[DeduplicationTestRecord, Int](
      deduplicationScenario("error-handling", value = "1 / #input.amount", filterCondition = "true"),
      data,
      timestampAssigner = Some(watermarkStrategy)
    )

    val validResult = result.validValue
    validResult.successes shouldBe List(10, 20)
    val errors = validResult.errors
    errors should have size 1
    errors.head.nodeId shouldBe Some("deduplication")
    errors.head.throwable.getMessage should include("/ by zero")
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
