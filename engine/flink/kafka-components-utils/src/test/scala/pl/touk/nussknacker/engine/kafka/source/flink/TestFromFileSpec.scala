package pl.touk.nussknacker.engine.kafka.source.flink

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.Json.{Null, fromString, obj}
import org.apache.flink.configuration.Configuration
import org.apache.kafka.common.record.TimestampType
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.flink.util.sink.SingleValueSinkFactory.SingleValueParamName
import pl.touk.nussknacker.engine.kafka.KafkaFactory.TopicParamName
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryProcessConfigCreator.ResultsHolders
import pl.touk.nussknacker.engine.process.runner.FlinkTestMain
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, KafkaConfigProperties}

import java.net.URL
import java.util.Collections

class TestFromFileSpec
    extends AnyFunSuite
    with Matchers
    with LazyLogging
    with EitherValuesDetailedMessage
    with OptionValues {

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
      configCreator = new KafkaSourceFactoryProcessConfigCreator(() => TestFromFileSpec.resultsHolders),
      modelClassLoader = new ModelClassLoader(getClass.getClassLoader, FlinkTestConfiguration.classpathWorkaround)
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
    val inputMetaAsJson = Json.fromFields(
      Map(
        "key"           -> Json.Null,
        "topic"         -> Json.fromString(topic),
        "partition"     -> Json.fromInt(0),
        "offset"        -> Json.fromInt(1),
        "timestamp"     -> Json.fromLong(expectedTimestamp),
        "timestampType" -> Json.fromString("CreateTime"),
        "headers"       -> Json.fromFields(List.empty),
        "leaderEpoch"   -> Json.fromInt(0)
      )
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

    val consumerRecord = ToJsonEncoder.defaultForTests
      .encode(inputMeta)
      .mapObject(
        _.add("value", obj("id" -> fromString("fooId"), "field" -> fromString("fooField")))
      )

    val results = run(process, ScenarioTestData(ScenarioTestJsonRecord("start", consumerRecord) :: Nil))

    val testResultVars = results.nodeResults("end").head.variables
    testResultVars("extractedTimestamp").hcursor.downField("pretty").as[Long].rightValue shouldBe expectedTimestamp
    testResultVars("inputMeta").hcursor.downField("pretty").focus.value shouldBe inputMetaAsJson
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
    val consumerRecord = ToJsonEncoder.defaultForTests
      .encode(inputMeta)
      .mapObject(
        _.add("key", Null)
          .add("value", obj("id" -> fromString("1234"), "field" -> fromString("abcd")))
      )

    val results = run(process, ScenarioTestData(ScenarioTestJsonRecord("start", consumerRecord) :: Nil))

    results.nodeResults shouldBe Symbol("nonEmpty")
  }

  private def run(process: CanonicalProcess, scenarioTestData: ScenarioTestData): TestResults[Json] = {
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      FlinkTestMain.run(
        modelData,
        process,
        scenarioTestData,
        FlinkTestConfiguration.setupMemory(new Configuration),
      )
    }
  }

}

object TestFromFileSpec extends Serializable {

  private val resultsHolders = new ResultsHolders

}
