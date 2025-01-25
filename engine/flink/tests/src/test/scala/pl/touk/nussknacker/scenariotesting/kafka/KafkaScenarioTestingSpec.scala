package pl.touk.nussknacker.scenariotesting.kafka

import cats.effect.unsafe.implicits.global
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.Json.{Null, fromString, obj}
import org.apache.flink.configuration.Configuration
import org.apache.kafka.common.record.TimestampType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.flink.util.sink.SingleValueSinkFactory.SingleValueParamName
import pl.touk.nussknacker.engine.kafka.KafkaFactory.TopicParamName
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryProcessConfigCreator
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryProcessConfigCreator.ResultsHolders
import pl.touk.nussknacker.engine.management.ScenarioTestingConfig
import pl.touk.nussknacker.engine.management.scenariotesting.{
  FlinkProcessTestRunner,
  ScenarioTestingMiniClusterWrapperFactory
}
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder
import pl.touk.nussknacker.engine.util.loader.{DeploymentManagersClassLoader, ModelClassLoader}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, KafkaConfigProperties}

import java.util.Collections

class KafkaScenarioTestingSpec
    extends AnyFunSuite
    with Matchers
    with LazyLogging
    with EitherValuesDetailedMessage
    with OptionValues
    with BeforeAndAfterAll {

  private val config = ConfigFactory
    .empty()
    .withValue(KafkaConfigProperties.bootstrapServersProperty(), fromAnyRef("kafka_should_not_be_used:9092"))
    .withValue(
      KafkaConfigProperties.property("schema.registry.url"),
      fromAnyRef("schema_registry_should_not_be_used:8081")
    )
    .withValue("kafka.topicsExistenceValidationConfig.enabled", fromAnyRef(false))

  private val (deploymentManagersClassLoaderInstance, releaseDeploymentManagersClassLoaderResources) =
    DeploymentManagersClassLoader
      .create(List.empty)
      .allocated
      .unsafeRunSync()

  private val modelData: ModelData =
    LocalModelData(
      inputConfig = config,
      components = List.empty,
      configCreator = new KafkaSourceFactoryProcessConfigCreator(() => KafkaScenarioTestingSpec.resultsHolders),
      modelClassLoader =
        ModelClassLoader(FlinkTestConfiguration.classpathWorkaround, None, deploymentManagersClassLoaderInstance)
    )

  private val scenarioTestingMiniClusterWrapper = ScenarioTestingMiniClusterWrapperFactory.create(
    modelData.modelClassLoader,
    parallelism = 1,
    miniClusterConfig = ScenarioTestingConfig.defaultMiniClusterConfig,
    streamExecutionConfig = new Configuration
  )

  private val testRunner =
    new FlinkProcessTestRunner(modelData, Some(scenarioTestingMiniClusterWrapper))

  override protected def afterAll(): Unit = {
    super.afterAll()
    scenarioTestingMiniClusterWrapper.close()
    releaseDeploymentManagersClassLoaderResources.unsafeRunSync()
  }

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

    val results = testRunner.runTests(process, ScenarioTestData(ScenarioTestJsonRecord("start", consumerRecord) :: Nil))

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

    val results = testRunner.runTests(process, ScenarioTestData(ScenarioTestJsonRecord("start", consumerRecord) :: Nil))

    results.nodeResults shouldBe Symbol("nonEmpty")
  }

}

object KafkaScenarioTestingSpec extends Serializable {

  private val resultsHolders = new ResultsHolders

}
