package pl.touk.nussknacker.engine.kafka.source.flink

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json.{Null, fromString, obj}
import org.apache.kafka.common.record.TimestampType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.flink.util.sink.SingleValueSinkFactory.SingleValueParamName
import pl.touk.nussknacker.engine.kafka.KafkaFactory.TopicParamName
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryProcessConfigCreator.ResultsHolders
import pl.touk.nussknacker.engine.kafka.source.{InputMeta, InputMetaToJson}
import pl.touk.nussknacker.engine.process.runner.FlinkTestMain
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.test.KafkaConfigProperties

import java.util.Collections

class TestFromFileSpec extends AnyFunSuite with Matchers with LazyLogging {

  private lazy val config = ConfigFactory
    .empty()
    .withValue(KafkaConfigProperties.bootstrapServersProperty(), fromAnyRef("notused:1111"))
    .withValue(KafkaConfigProperties.property("schema.registry.url"), fromAnyRef("notused:2222"))

  protected lazy val modelData: ModelData =
    LocalModelData(
      config,
      List.empty,
      configCreator = new KafkaSourceFactoryProcessConfigCreator(() => TestFromFileSpec.resultsHolders)
    )

  test("Should pass correct timestamp from test data") {
    val topic             = "simple"
    val expectedTimestamp = System.currentTimeMillis()
    val inputMeta =
      InputMeta(null, topic, 0, 1, expectedTimestamp, TimestampType.CREATE_TIME, Collections.emptyMap(), 0)

    val process = ScenarioBuilder
      .streaming("test")
      .source(
        "start",
        "kafka-jsonValueWithMeta",
        TopicParamName -> s"'$topic'",
      )
      .customNode("transform", "extractedTimestamp", "extractAndTransformTimestamp", "timestampToSet" -> "0L")
      .emptySink("end", "sinkForInputMeta", SingleValueParamName -> "#inputMeta")

    val consumerRecord = new InputMetaToJson()
      .encoder(BestEffortJsonEncoder.defaultForTests.encode)
      .apply(inputMeta)
      .mapObject(
        _.add("key", Null)
          .add("value", obj("id" -> fromString("fooId"), "field" -> fromString("fooField")))
      )

    val results = run(process, ScenarioTestData(ScenarioTestJsonRecord("start", consumerRecord) :: Nil))

    val testResultVars = results.nodeResults("end").head.variables
    testResultVars.get("extractedTimestamp") shouldBe Some(expectedTimestamp)
    testResultVars.get("inputMeta") shouldBe Some(inputMeta)
  }

  test("should test source emitting event extending DisplayWithEncoder") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "kafka-jsonValueWithMeta", TopicParamName -> "'test.topic'")
      .emptySink("end", "sinkForInputMeta", SingleValueParamName -> "#inputMeta")
    val inputMeta = InputMeta(
      null,
      "test.topic",
      0,
      1,
      System.currentTimeMillis(),
      TimestampType.CREATE_TIME,
      Collections.emptyMap(),
      0
    )
    val consumerRecord = new InputMetaToJson()
      .encoder(BestEffortJsonEncoder.defaultForTests.encode)
      .apply(inputMeta)
      .mapObject(
        _.add("key", Null)
          .add("value", obj("id" -> fromString("1234"), "field" -> fromString("abcd")))
      )

    val results = run(process, ScenarioTestData(ScenarioTestJsonRecord("start", consumerRecord) :: Nil))

    results.nodeResults shouldBe Symbol("nonEmpty")
  }

  private def run(process: CanonicalProcess, scenarioTestData: ScenarioTestData): TestResults = {
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      FlinkTestMain.run(
        modelData,
        process,
        scenarioTestData,
        FlinkTestConfiguration.configuration()
      )
    }
  }

}

object TestFromFileSpec extends Serializable {

  private val resultsHolders = new ResultsHolders

}
