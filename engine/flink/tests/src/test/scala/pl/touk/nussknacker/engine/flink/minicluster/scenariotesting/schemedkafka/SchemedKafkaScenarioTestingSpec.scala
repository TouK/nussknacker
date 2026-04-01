package pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.schemedkafka

import cats.effect.unsafe.IORuntime
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.Json._
import org.apache.avro.Schema
import org.scalatest.{BeforeAndAfterAll, LoneElement, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.{ContextId, NodeId}
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.parameter.{ParameterName, ValueInputWithFixedValuesProvided}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestSourceSpecificFormatJsonRecord}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.classloader.ModelClassLoader
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.FlinkMiniClusterScenarioTestRunner
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.schemedkafka.SchemedKafkaScenarioTestingSpec._
import pl.touk.nussknacker.engine.flink.minicluster.util.DurationToRetryPolicyConverter
import pl.touk.nussknacker.engine.flink.util.sink.SingleValueSinkFactory.SingleValueParamName
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{
  schemaVersionParamName,
  topicParamName
}
import pl.touk.nussknacker.engine.schemedkafka.TestFlinkKafkaComponentProvider
import pl.touk.nussknacker.engine.schemedkafka.schema.{Address, Company}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, KafkaConfigProperties, VeryPatientScalaFutures}

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class SchemedKafkaScenarioTestingSpec
    extends AnyFunSuite
    with Matchers
    with LazyLogging
    with EitherValuesDetailedMessage
    with OptionValues
    with LoneElement
    with BeforeAndAfterAll
    with VeryPatientScalaFutures {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val ioRuntime: IORuntime = IORuntime.global

  private val provider: TestFlinkKafkaComponentProvider =
    new TestFlinkKafkaComponentProvider(
      MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient),
      sinkForInputMetaResultsHolder
    )

  private val modelConfig = ConfigFactory
    .empty()
    .withValue(
      KafkaConfigProperties.bootstrapServersProperty("components.kafka.config"),
      fromAnyRef("kafka_should_not_be_used:9092")
    )
    .withValue(
      KafkaConfigProperties.property("components.kafka.config", "schema.registry.url"),
      fromAnyRef("schema_registry_should_not_be_used:8081")
    )
    .withValue("components.kafka.config.topicsExistenceValidationConfig.enabled", fromAnyRef(false))
    .withValue("components.kafka.config.optimizedGenericRecordSerialization.enabled", fromAnyRef(false))

  private val modelData =
    LocalModelData(
      modelConfig,
      provider.createComponents(ModelConfig.parse(modelConfig)),
      modelClassLoader = ModelClassLoader.flinkWorkAroundEmptyClassloader
    )

  private val miniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private val testRunner =
    new FlinkMiniClusterScenarioTestRunner(
      modelData.toModelDataProvider,
      miniClusterWithServices,
      parallelism = 1,
      waitForJobIsFinishedRetryPolicy =
        DurationToRetryPolicyConverter.toPausePolicy(patienceConfig.timeout - 3.seconds, patienceConfig.interval * 2)
    )

  override protected def afterAll(): Unit = {
    super.afterAll()
    miniClusterWithServices.close()
  }

  test("Should pass correct timestamp from test data") {
    val topic          = UnspecializedTopicName("address")
    val givenTimestamp = System.currentTimeMillis()
    val id             = registerSchema(topic, Address.schema)

    val process = ScenarioBuilder
      .streaming("test")
      .source(
        "start",
        "kafka",
        topicParamName.value         -> s"'${topic.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
      )
      .customNode("transform", "extractedTimestamp", "extractAndTransformTimestamp", "timestampToSet" -> "0L".spel)
      .emptySink("end", "sinkForInputMeta", SingleValueParamName -> "#inputMeta".spel)

    val consumerRecordJson =
      Json.fromFields(
        Map(
          "key"           -> Json.Null,
          "topic"         -> Json.fromString(topic.name),
          "partition"     -> Json.fromInt(0),
          "offset"        -> Json.fromInt(1),
          "timestamp"     -> Json.fromLong(givenTimestamp),
          "timestampType" -> Json.fromString("CreateTime"),
          "headers"       -> Json.fromFields(List.empty),
          "leaderEpoch"   -> Json.fromInt(0),
          "value"         -> obj("city" -> fromString("Lublin"), "street" -> fromString("Lipowa"))
        )
      )
    val testRecordJson =
      obj("keySchemaId" -> Null, "valueSchemaId" -> fromInt(id), "consumerRecord" -> consumerRecordJson)
    val scenarioTestData =
      ScenarioTestData(
        ScenarioTestSourceSpecificFormatJsonRecord(NodeId("start"), testRecordJson, timestamp = None) :: Nil
      )

    val results = testRunner.runTests(process, scenarioTestData).futureValue

    val testResultVars = results.nodeResults(NodeId("end")).head.variables
    testResultVars("extractedTimestamp").hcursor.downField("pretty").as[Long].rightValue shouldBe givenTimestamp
    val expectedInputMetaInTestResults = Json.fromFields(
      Map(
        "key"           -> Json.Null,
        "topic"         -> Json.fromString(topic.name),
        "partition"     -> Json.fromInt(0),
        "offset"        -> Json.fromInt(1),
        "timestamp"     -> Json.fromLong(givenTimestamp),
        "timestampType" -> Json.fromString("CREATE_TIME"),
        "headers"       -> Json.fromFields(List.empty),
        "leaderEpoch"   -> Json.fromInt(0)
      )
    )
    testResultVars("inputMeta").hcursor.downField("pretty").focus.value shouldBe expectedInputMetaInTestResults
  }

  test("Should pass parameters correctly and use them in scenario test") {
    val topic = UnspecializedTopicName("company")
    registerSchema(topic, Company.schema)
    val process = ScenarioBuilder
      .streaming("test")
      .source(
        "start",
        "kafka",
        topicParamName.value         -> s"'${topic.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
      )
      .customNode("transform", "extractedTimestamp", "extractAndTransformTimestamp", "timestampToSet" -> "0L".spel)
      .emptySink(
        "end",
        "sinkForInputMeta",
        SingleValueParamName -> "#input.name + '-' + #input.address.city + '-' + #input.address.street".spel
      )

    val parameterExpressions = Map(
      ParameterName("name")           -> Expression.spel("'TouK'"),
      ParameterName("address.city")   -> Expression.spel("'Warszawa'"),
      ParameterName("address.street") -> Expression.spel("'Bohaterów Września'"),
    )
    val scenarioTestData = ScenarioTestData("start", parameterExpressions)

    val results = testRunner.runTests(process, scenarioTestData).futureValue
    results
      .expressionEvaluationResults(NodeId("end"))
      .head
      .value
      .hcursor
      .downField("pretty")
      .as[String]
      .rightValue shouldBe "TouK-Warszawa-Bohaterów Września"

  }

  test("should handle fragment test parameters in test") {
    val fragment = ScenarioBuilder
      .fragment("fragment1", "in" -> classOf[String])
      .filter("filter", "#in != 'stop'".spel)
      .fragmentOutput("fragmentEnd", "output", "out" -> "#in".spel)

    val parameterExpressions = Map(
      ParameterName("in") -> Expression.spel("'some-text-id'")
    )
    val scenarioTestData = ScenarioTestData("fragment1", parameterExpressions)
    val results          = testRunner.runTests(fragment, scenarioTestData).futureValue

    nodeResults(results, "fragment1").loneElement shouldBe (
      ContextId(
        scenarioName = ProcessName("fragment1"),
        originatingNodeId = NodeId("fragment1"),
        taskId = 0,
        index = 0
      ),
      Map(
        "in" -> Json.fromFields(Seq("pretty" -> Json.fromString("some-text-id")))
      )
    )

    nodeResults(results, "fragmentEnd").loneElement shouldBe (
      ContextId(
        scenarioName = ProcessName("fragment1"),
        originatingNodeId = NodeId("fragment1"),
        taskId = 0,
        index = 0
      ),
      Map(
        "in"  -> Json.fromFields(Seq("pretty" -> Json.fromString("some-text-id"))),
        "out" -> Json.fromFields(Seq("pretty" -> Json.fromString("some-text-id")))
      )
    )

    val mockedTimestamp = Instant.now()
    results
      .expressionEvaluationResults(NodeId("fragmentEnd"))
      .loneElement
      .copy(timestamp = mockedTimestamp) shouldBe ExpressionEvaluationResult(
      ContextId(
        scenarioName = ProcessName("fragment1"),
        originatingNodeId = NodeId("fragment1"),
        taskId = 0,
        index = 0
      ),
      mockedTimestamp,
      "out",
      Json.fromFields(Seq("pretty" -> Json.fromString("some-text-id")))
    )

    results.exceptions shouldBe empty
  }

  test("should handle fragment test parameters in test when the input parameter is required") {
    val fragmentFixedParameter = FragmentParameter(
      ParameterName("in"),
      FragmentClazzRef[java.lang.String],
      required = true,
      initialValue = None,
      hintText = None,
      valueEditor = Some(
        ValueInputWithFixedValuesProvided(
          fixedValuesList = List(
            FixedExpressionValue("'uno'", "uno"),
            FixedExpressionValue("'due'", "due"),
          ),
          allowOtherValue = false
        )
      ),
      valueCompileTimeValidation = None
    )
    val fragment = ScenarioBuilder
      .fragmentWithRawParameters("fragment1", fragmentFixedParameter)
      .filter("filter", "#in != 'stop'".spel)
      .fragmentOutput("fragmentEnd", "output", "out" -> "#in".spel)

    val parameterExpressions = Map(
      ParameterName("in") -> Expression.spel("'uno'")
    )
    val scenarioTestData = ScenarioTestData("fragment1", parameterExpressions)
    val results          = testRunner.runTests(fragment, scenarioTestData).futureValue

    nodeResults(results, "fragment1").loneElement shouldBe (
      ContextId(
        scenarioName = ProcessName("fragment1"),
        originatingNodeId = NodeId("fragment1"),
        taskId = 0,
        index = 0
      ),
      Map(
        "in" -> Json.fromFields(Seq("pretty" -> Json.fromString("uno")))
      )
    )

    nodeResults(results, "fragmentEnd").loneElement shouldBe (
      ContextId(
        scenarioName = ProcessName("fragment1"),
        originatingNodeId = NodeId("fragment1"),
        taskId = 0,
        index = 0
      ),
      Map(
        "in"  -> Json.fromFields(Seq("pretty" -> Json.fromString("uno"))),
        "out" -> Json.fromFields(Seq("pretty" -> Json.fromString("uno")))
      )
    )

    val mockedTimestamp = Instant.now()
    results
      .expressionEvaluationResults(NodeId("fragmentEnd"))
      .loneElement
      .copy(timestamp = mockedTimestamp) shouldBe ExpressionEvaluationResult(
      ContextId(
        scenarioName = ProcessName("fragment1"),
        originatingNodeId = NodeId("fragment1"),
        taskId = 0,
        index = 0
      ),
      mockedTimestamp,
      "out",
      Json.fromFields(Seq("pretty" -> Json.fromString("uno")))
    )

    results.exceptions shouldBe empty
  }

  private def registerSchema(topic: UnspecializedTopicName, schema: Schema) = {
    val subject      = ConfluentUtils.topicSubject(topic, isKey = false)
    val parsedSchema = ConfluentUtils.convertToAvroSchema(schema)
    schemaRegistryMockClient.register(subject, parsedSchema)
  }

  private def nodeResults[T](results: TestProcess.TestResults[T], key: String) =
    results.nodeResults(NodeId(key)).map(r => (r.id, r.variables))

}

object SchemedKafkaScenarioTestingSpec {

  private val sinkForInputMetaResultsHolder = new TestResultsHolder[java.util.Map[String @unchecked, _]]

}
