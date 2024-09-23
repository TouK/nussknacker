package pl.touk.nussknacker.streaming.embedded

import io.circe.Json
import io.circe.Json.{fromInt, fromString, obj}
import org.scalatest.OptionValues
import pl.touk.nussknacker.engine.api.{DisplayJsonWithEncoder, ProcessVersion}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.runtimecontext.IncContextIdGenerator
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.definition.test.ModelDataTestInfoProvider
import pl.touk.nussknacker.engine.deployment.{DeploymentData, User}
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testmode.TestProcess.ExpressionInvocationResult
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class StreamingEmbeddedDeploymentManagerTest
    extends BaseStreamingEmbeddedDeploymentManagerTest
    with OptionValues
    with EitherValuesDetailedMessage {

  protected implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
  import KafkaUniversalComponentTransformer._

  test("Deploys scenario and cancels") {
    val fixture @ FixtureParam(manager, _, inputTopic, outputTopic) = prepareFixture()

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
      manager.getProcessStates(name).futureValue.value.map(_.status) shouldBe List(SimpleStateStatus.Running)
    }

    val input = obj("productId" -> fromInt(10))
    kafkaClient.sendMessage(inputTopic.name, input.noSpaces).futureValue
    kafkaClient.createConsumer().consumeWithJson[Json](outputTopic.name).take(1).head.message() shouldBe input

    wrapInFailingLoader {
      manager.processCommand(DMCancelScenarioCommand(name, User("a", "b"))).futureValue
    }
    manager.getProcessStates(name).futureValue.value shouldBe List.empty
  }

  test("Run persisted scenario deployments") {
    val inputTopic  = generateInputTopicName
    val outputTopic = generateOutputTopicName
    val name        = ProcessName("testName")
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

    val deployedScenarioData =
      DeployedScenarioData(ProcessVersion.empty.copy(processName = name), DeploymentData.empty, scenario)
    val FixtureParam(manager, _, _, _) = prepareFixture(inputTopic, outputTopic, List(deployedScenarioData))

    eventually {
      manager.getProcessStates(name).futureValue.value.map(_.status) shouldBe List(SimpleStateStatus.Running)
    }

    val input = obj("productId" -> fromInt(10))
    kafkaClient.sendMessage(inputTopic.name, input.noSpaces).futureValue

    kafkaClient.createConsumer().consumeWithJson[Json](outputTopic.name).take(1).head.message() shouldBe input

    manager.processCommand(DMCancelScenarioCommand(name, User("a", "b"))).futureValue
    manager.getProcessStates(name).futureValue.value shouldBe List.empty
  }

  test("Run persisted scenario deployment with scenario json incompatible with current component API") {
    val inputTopic  = generateInputTopicName
    val outputTopic = generateOutputTopicName
    val name        = ProcessName("testName")
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

    val deployedScenarioData = DeployedScenarioData(
      ProcessVersion.empty.copy(processName = name),
      DeploymentData.empty,
      scenarioWithIncompatibleParameters
    )
    val FixtureParam(manager, _, _, _) = prepareFixture(inputTopic, outputTopic, List(deployedScenarioData))

    manager.getProcessStates(name).futureValue.value.map(_.status) should matchPattern {
      case ProblemStateStatus("Scenario compilation errors", _) :: Nil =>
    }
  }

  test(
    "Deploy scenario json incompatible with current component API should throw exception immediately instead of moving scenario to Failed state"
  ) {
    val fixture @ FixtureParam(_, _, inputTopic, outputTopic) = prepareFixture()
    val name                                                  = ProcessName("testName")
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

    val fixture @ FixtureParam(manager, _, inputTopic, outputTopic) = prepareFixture(jsonSchema = schema)

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
      manager.getProcessStates(name).futureValue.value.map(_.status) shouldBe List(SimpleStateStatus.Running)
    }

    kafkaClient.sendMessage(inputTopic.name, message("2")).futureValue
    consumer.take(2) shouldBe List(prefixMessage("start", "1"), prefixMessage("next", "2"))

    kafkaClient.sendMessage(inputTopic.name, message("3")).futureValue
    consumer.take(3) shouldBe List(prefixMessage("start", "1"), prefixMessage("next", "2"), prefixMessage("next", "3"))

    manager.processCommand(DMCancelScenarioCommand(name, User("a", "b"))).futureValue

    manager.getProcessStates(name).futureValue.value shouldBe List.empty
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

    val FixtureParam(manager, modelData, inputTopic, outputTopic) = prepareFixture(jsonSchema = schema)
    val testInfoProvider                                          = new ModelDataTestInfoProvider(modelData)

    def message(input: String) = obj("message" -> fromString(input)).noSpaces

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

    kafkaClient.sendMessage(inputTopic.name, message("1")).futureValue
    kafkaClient.sendMessage(inputTopic.name, message("2")).futureValue

    val processVersion      = ProcessVersion.empty.copy(processName = scenario.metaData.name)
    val preliminaryTestData = testInfoProvider.generateTestData(processVersion, scenario, 2).rightValue

    val testData = testInfoProvider.prepareTestData(preliminaryTestData, scenario).rightValue
    val results = wrapInFailingLoader {
      manager.processCommand(DMTestScenarioCommand(processVersion, scenario, testData)).futureValue
    }
    results.nodeResults("sink") should have length 2
    val idGenerator       = IncContextIdGenerator.withProcessIdNodeIdPrefix(scenario.metaData, "source")
    val invocationResults = results.invocationResults("sink")
    val id1               = idGenerator.nextContextId()
    val id2               = idGenerator.nextContextId()

    invocationResults.toSet shouldBe Set(
      ExpressionInvocationResult(id1, "Key", Json.Null),
      ExpressionInvocationResult(id1, sinkValueParamName.value, variable(Map("message" -> "1", "other" -> "1"))),
      ExpressionInvocationResult(id2, "Key", Json.Null),
      ExpressionInvocationResult(id2, sinkValueParamName.value, variable(Map("message" -> "2", "other" -> "1")))
    )

  }

  private def variable(value: Map[String, String]): Json =
    Json.obj("pretty" -> Json.fromFields(value.mapValuesNow(Json.fromString)))

}
