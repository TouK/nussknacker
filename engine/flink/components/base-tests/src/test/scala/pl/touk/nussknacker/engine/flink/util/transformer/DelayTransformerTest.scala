package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInfo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
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

  private def createScenario(
      processName: ProcessName,
      sourceComponentId: String,
      delay: String,
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
        "delay"    -> s"T(java.time.Duration).parse('$delay')".spel,
        "timeMode" -> s"'$timeMode'".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#input.value".spel)

}

object DelayTestRecord {
  class TypeInfoFactory extends CaseClassTypeInfoFactory[DelayTestRecord]
}

@TypeInfo(classOf[DelayTestRecord.TypeInfoFactory])
case class DelayTestRecord(key: String, value: Int, eventTimestamp: Long)
