package pl.touk.nussknacker.engine.kafka.source.flink

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
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
import pl.touk.nussknacker.engine.kafka.source.{InputMeta, InputMetaToJsonCustomisation}
import pl.touk.nussknacker.engine.process.runner.FlinkTestMain
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder
import pl.touk.nussknacker.test.KafkaConfigProperties

import java.util.Collections

class TestFromFileSpec extends AnyFunSuite with Matchers with LazyLogging {

  private lazy val config = ConfigFactory
    .empty()
    .withValue(KafkaConfigProperties.bootstrapServersProperty(), fromAnyRef("kafka_should_not_be_used:9092"))
    .withValue(
      KafkaConfigProperties.property("schema.registry.url"),
      fromAnyRef("schema_registry_should_not_be_used:8081")
    )
    .withValue("kafka.topicsExistenceValidationConfig.enabled", fromAnyRef(false))

  protected lazy val modelData: ModelData =
    LocalModelData(
      inputConfig = config,
      components = List.empty,
      configCreator = new KafkaSourceFactoryProcessConfigCreator(() => TestFromFileSpec.resultsHolders)
    )

  test("Should pass correct timestamp from test data") {
    val topic             = "simple"
    val expectedTimestamp = System.currentTimeMillis()
    val inputMeta = InputMeta(
      key = null,
      topic = topic,
      partition = 0,
      offset = 1,
      timestamp = expectedTimestamp,
      timestampType = TimestampType.CREATE_TIME,
      headers = Collections.emptyMap(),
      leaderEpoch = 0
    )

    val process = ScenarioBuilder
      .streaming("test")
      .source(
        "start",
        "kafka-jsonValueWithMeta",
        TopicParamName.value -> s"'$topic'".spel,
      )
      .customNode("transform", "extractedTimestamp", "extractAndTransformTimestamp", "timestampToSet" -> "0L".spel)
      .emptySink("end", "sinkForInputMeta", SingleValueParamName -> "#inputMeta".spel)

    val consumerRecord = new InputMetaToJsonCustomisation()
      .encoder(ToJsonEncoder.defaultForTests.encode)
      .apply(inputMeta)
      .mapObject(
        _.add("key", Null)
          .add("value", obj("id" -> fromString("fooId"), "field" -> fromString("fooField")))
      )

    val results = run(process, ScenarioTestData(ScenarioTestJsonRecord("start", consumerRecord) :: Nil))

    val testResultVars = results.nodeResults("end").head.variables
    testResultVars("extractedTimestamp") shouldBe variable(expectedTimestamp)
    testResultVars("inputMeta") shouldBe variable(inputMeta)
  }

  test("should test source emitting event extending DisplayWithEncoder") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "kafka-jsonValueWithMeta", TopicParamName.value -> "'test.topic'".spel)
      .emptySink("end", "sinkForInputMeta", SingleValueParamName -> "#inputMeta".spel)
    val inputMeta = InputMeta(
      key = null,
      topic = "test.topic",
      partition = 0,
      offset = 1,
      timestamp = System.currentTimeMillis(),
      timestampType = TimestampType.CREATE_TIME,
      headers = Collections.emptyMap(),
      leaderEpoch = 0
    )
    val consumerRecord = new InputMetaToJsonCustomisation()
      .encoder(ToJsonEncoder.defaultForTests.encode)
      .apply(inputMeta)
      .mapObject(
        _.add("key", Null)
          .add("value", obj("id" -> fromString("1234"), "field" -> fromString("abcd")))
      )

    val results = run(process, ScenarioTestData(ScenarioTestJsonRecord("start", consumerRecord) :: Nil))

    results.nodeResults shouldBe Symbol("nonEmpty")
  }

  private def run(process: CanonicalProcess, scenarioTestData: ScenarioTestData): TestResults[_] = {
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      FlinkTestMain.run(
        modelData,
        process,
        scenarioTestData,
        FlinkTestConfiguration.configuration(),
      )
    }
  }

  private def variable(value: Any) = {
    val json = value match {
      case im: InputMeta[_] =>
        new InputMetaToJsonCustomisation()
          .encoder(ToJsonEncoder.defaultForTests.encode)
          .apply(im)
      case ln: Long => Json.fromLong(ln)
      case any      => Json.fromString(any.toString)
    }
    Json.obj("pretty" -> json)
  }

}

object TestFromFileSpec extends Serializable {

  private val resultsHolders = new ResultsHolders

}
