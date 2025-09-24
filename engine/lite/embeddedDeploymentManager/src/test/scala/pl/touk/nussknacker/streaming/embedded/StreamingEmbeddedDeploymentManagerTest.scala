package pl.touk.nussknacker.streaming.embedded

import io.circe.Json
import io.circe.Json._
import io.circe.syntax._
import org.scalatest.OptionValues
import pl.touk.nussknacker.engine.api.{NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.runtimecontext.IncContextIdGenerator
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestSourceSpecificFormatJsonRecord}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.User
import pl.touk.nussknacker.engine.kafka.consumerrecord.SerializableConsumerRecord
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter.SchemaBasedSerializableConsumerRecord
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testmode.TestProcess.ExpressionEvaluationResult
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

import java.time.Instant
import scala.concurrent.duration._

class StreamingEmbeddedDeploymentManagerTest
    extends BaseStreamingEmbeddedDeploymentManagerTest
    with OptionValues
    with EitherValuesDetailedMessage {

  protected implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer

  import KafkaUniversalComponentTransformer._
  import SchemaBasedSerializableConsumerRecord._

  private val mockedTimestamp = Instant.now()

  test("Deploys scenario and cancels") {
    val fixture @ FixtureParam(manager, inputTopic, outputTopic) = prepareFixture()

    val name = ProcessName("testName")
    val scenario = ScenarioBuilder
      .streamingLite(name.value)
      .source(
        "source",
        "kafka",
        topicParamName.value         -> s"'${inputTopic.name}'".spel,
        schemaVersionParamName.value -> "'latest'".spel
      )
      .emptySink(
        "sink",
        "kafka",
        topicParamName.value              -> s"'${outputTopic.name}'".spel,
        schemaVersionParamName.value      -> "'latest'".spel,
        "Key"                             -> "null".spel,
        sinkRawEditorParamName.value      -> "true".spel,
        sinkValidationModeParamName.value -> "'strict'".spel,
        sinkValueParamName.value          -> "#input".spel
      )

    wrapInFailingLoader {
      fixture.deployScenario(scenario)
    }

    eventually {
      manager.getScenarioDeploymentsStatuses(name).futureValue.value.map(_.status) should matchPattern {
        case SimpleStateStatus.Running(VersionId(1), startedAt) :: Nil if isWithinLast(startedAt, 10 second) =>
      }
    }

    val input = obj("productId" -> fromInt(10))
    kafkaClient.sendMessage(inputTopic.name, input.noSpaces).futureValue
    kafkaClient.createConsumer().consumeWithJson[Json](outputTopic.name).take(1).head.message() shouldBe input

    wrapInFailingLoader {
      manager.processCommand(DMCancelScenarioCommand(name, User("a", "b"))).futureValue
    }
    manager.getScenarioDeploymentsStatuses(name).futureValue.value shouldBe List.empty
  }

  test(
    "Deploy scenario json incompatible with current component API should throw exception immediately instead of moving scenario to Failed state"
  ) {
    val fixture @ FixtureParam(_, inputTopic, outputTopic) = prepareFixture()
    val name                                               = ProcessName("testName")
    // We simulate scenario json incompatible with component API by replacing parameter name with some other name
    val scenarioWithIncompatibleParameters = ScenarioBuilder
      .streamingLite(name.value)
      .source(
        "source",
        "kafka",
        "Old Topic param"            -> s"'${inputTopic.name}'".spel,
        schemaVersionParamName.value -> "'latest'".spel
      )
      .emptySink(
        "sink",
        "kafka",
        topicParamName.value              -> s"'${outputTopic.name}'".spel,
        schemaVersionParamName.value      -> "'latest'".spel,
        "Key"                             -> "null".spel,
        sinkRawEditorParamName.value      -> "true".spel,
        sinkValidationModeParamName.value -> "'strict'".spel,
        sinkValueParamName.value          -> "#input".spel
      )

    an[Exception] shouldBe thrownBy {
      fixture.deployScenario(scenarioWithIncompatibleParameters)
    }
  }

  test("Redeploys scenario") {
    val schema =
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "message": {
        |      "type": "string"
        |    },
        |    "prefix": {
        |      "type": "string"
        |    }
        |  },
        |  "required": [ "message" ]
        |}
        |""".stripMargin

    val fixture @ FixtureParam(manager, inputTopic, outputTopic) = prepareFixture(jsonSchema = schema)

    val name = ProcessName("testName")
    def scenarioForOutput(outputPrefix: String) = ScenarioBuilder
      .streamingLite(name.value)
      .source(
        "source",
        "kafka",
        topicParamName.value         -> s"'${inputTopic.name}'".spel,
        schemaVersionParamName.value -> "'latest'".spel
      )
      .emptySink(
        "sink",
        "kafka",
        topicParamName.value              -> s"'${outputTopic.name}'".spel,
        schemaVersionParamName.value      -> "'latest'".spel,
        "Key"                             -> "null".spel,
        sinkRawEditorParamName.value      -> "true".spel,
        sinkValidationModeParamName.value -> "'strict'".spel,
        sinkValueParamName.value          -> s"{message: #input.message, prefix: '$outputPrefix'}".spel
      )
    def message(input: String) = obj("message" -> fromString(input)).noSpaces
    def prefixMessage(prefix: String, message: String) =
      obj("message" -> fromString(message), "prefix" -> fromString(prefix))

    fixture.deployScenario(scenarioForOutput("start"))

    kafkaClient.sendMessage(inputTopic.name, message("1")).futureValue

    val consumer = kafkaClient.createConsumer().consumeWithJson[Json](outputTopic.name).map(_.message())
    consumer.head shouldBe prefixMessage("start", "1")

    fixture.deployScenario(scenarioForOutput("next"))

    eventually {
      manager.getScenarioDeploymentsStatuses(name).futureValue.value.map(_.status) should matchPattern {
        case SimpleStateStatus.Running(VersionId(1), startedAt) :: Nil if isWithinLast(startedAt, 10 second) =>
      }
    }

    kafkaClient.sendMessage(inputTopic.name, message("2")).futureValue
    consumer.take(2) shouldBe List(prefixMessage("start", "1"), prefixMessage("next", "2"))

    kafkaClient.sendMessage(inputTopic.name, message("3")).futureValue
    consumer.take(3) shouldBe List(prefixMessage("start", "1"), prefixMessage("next", "2"), prefixMessage("next", "3"))

    manager.processCommand(DMCancelScenarioCommand(name, User("a", "b"))).futureValue

    manager.getScenarioDeploymentsStatuses(name).futureValue.value shouldBe List.empty
  }

  test("Performs test from file") {
    val schema =
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "message": {
        |      "type": "string"
        |    },
        |    "other": {
        |      "type": "string"
        |    }
        |  },
        |  "required": [ "message" ]
        |}
        |""".stripMargin

    val FixtureParam(manager, inputTopic, outputTopic) = prepareFixture(jsonSchema = schema)

    val name = ProcessName("testName")
    val scenario = ScenarioBuilder
      .streamingLite(name.value)
      .parallelism(1)
      .source(
        "source",
        "kafka",
        topicParamName.value         -> s"'${inputTopic.name}'".spel,
        schemaVersionParamName.value -> "'latest'".spel
      )
      .emptySink(
        "sink",
        "kafka",
        topicParamName.value              -> s"'${outputTopic.name}'".spel,
        schemaVersionParamName.value      -> "'latest'".spel,
        "Key"                             -> "null".spel,
        sinkRawEditorParamName.value      -> "true".spel,
        sinkValidationModeParamName.value -> "'strict'".spel,
        sinkValueParamName.value          -> s"{message: #input.message, other: '1'}".spel
      )

    val processVersion = ProcessVersion.empty.copy(processName = scenario.metaData.name)

    def testRecord(input: String): Json =
      SchemaBasedSerializableConsumerRecord(
        keySchemaId = None,
        valueSchemaId = None,
        consumerRecord = SerializableConsumerRecord[Json, Json](
          key = None,
          value = obj("message" -> fromString(input)),
          topic = None,
          partition = None,
          offset = None,
          timestamp = None,
          timestampType = None,
          headers = None,
          leaderEpoch = None
        )
      ).asJson

    val testData = ScenarioTestData(
      List(
        ScenarioTestSourceSpecificFormatJsonRecord(NodeId("source"), testRecord("1")),
        ScenarioTestSourceSpecificFormatJsonRecord(NodeId("source"), testRecord("2"))
      )
    )

    val results = wrapInFailingLoader {
      manager.processCommand(DMTestScenarioCommand(processVersion, scenario, testData)).futureValue
    }
    results.nodeResults(NodeId("sink")) should have length 2
    val idGenerator = IncContextIdGenerator.withProcessIdNodeIdPrefix(scenario.metaData, NodeId("source"), taskId = 0)
    val expressionEvaluationResults = results.expressionEvaluationResults(NodeId("sink"))
    val id1                         = idGenerator.nextContextId()
    val id2                         = idGenerator.nextContextId()

    expressionEvaluationResults.toSet.map(withMockedTimestamp) shouldBe Set(
      ExpressionEvaluationResult(id1, mockedTimestamp, "Key", Json.Null),
      ExpressionEvaluationResult(
        id1,
        mockedTimestamp,
        sinkValueParamName.value,
        variable(Map("message" -> "1", "other" -> "1"))
      ),
      ExpressionEvaluationResult(id2, mockedTimestamp, "Key", Json.Null),
      ExpressionEvaluationResult(
        id2,
        mockedTimestamp,
        sinkValueParamName.value,
        variable(Map("message" -> "2", "other" -> "1"))
      )
    )

  }

  private def variable(value: Map[String, String]): Json =
    Json.obj("pretty" -> Json.fromFields(value.mapValuesNow(Json.fromString)))

  private def withMockedTimestamp(result: ExpressionEvaluationResult[Json]) =
    result.copy(timestamp = mockedTimestamp)

}
