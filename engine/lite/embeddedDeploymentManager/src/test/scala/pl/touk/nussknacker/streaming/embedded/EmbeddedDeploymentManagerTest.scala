package pl.touk.nussknacker.streaming.embedded

import io.circe.Json.{fromString, obj}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{ExpressionInvocationResult, TestData}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.runtimecontext.IncContextIdGenerator
import pl.touk.nussknacker.engine.build.StreamingLiteScenarioBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.definition.ModelDataTestInfoProvider
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.streaming.embedded.EmbeddedStateStatus.DetailedFailedStateStatus

import scala.jdk.CollectionConverters.mapAsJavaMapConverter

class EmbeddedDeploymentManagerTest extends BaseEmbeddedDeploymentManagerTest {

  test("Deploys scenario and cancels") {
    val fixture@FixtureParam(manager, _, inputTopic, outputTopic) = prepareFixture()

    val name = ProcessName("testName")
    val scenario = StreamingLiteScenarioBuilder
      .id(name.value)
      .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")

    wrapInFailingLoader {
      fixture.deployScenario(scenario)
    }

    eventually {
      manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)
    }

    val input = obj("key" -> fromString("dummy"))
    kafkaClient.sendMessage(inputTopic, input.noSpaces).futureValue
    kafkaClient.createConsumer().consumeWithJson(outputTopic).head shouldBe input

    wrapInFailingLoader {
      manager.cancel(name, User("a", "b")).futureValue
    }
    manager.findJobStatus(name).futureValue shouldBe None
  }

  test("Run persisted scenario deployments") {
    val inputTopic = generateInputTopicName
    val outputTopic = generateInputTopicName
    val name = ProcessName("testName")
    val scenario = StreamingLiteScenarioBuilder
      .id(name.value)
      .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")

    val deployedScenarioData = DeployedScenarioData(ProcessVersion.empty.copy(processName = name), DeploymentData.empty, scenario)
    val FixtureParam(manager, _, _, _) = prepareFixture(inputTopic, outputTopic, List(deployedScenarioData))

    eventually {
      manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)
    }

    val input = obj("key" -> fromString("dummy"))
    kafkaClient.sendMessage(inputTopic, input.noSpaces).futureValue
    kafkaClient.createConsumer().consumeWithJson(outputTopic).head shouldBe input

    manager.cancel(name, User("a", "b")).futureValue

    manager.findJobStatus(name).futureValue shouldBe None
  }

  test("Run persisted scenario deployment with scenario json incompatible with current component API") {
    val inputTopic = generateInputTopicName
    val outputTopic = generateInputTopicName
    val name = ProcessName("testName")
    // We simulate scenario json incompatible with component API by replacing parameter name with some other name
    val scenarioWithIncompatibleParameters = StreamingLiteScenarioBuilder
      .id(name.value)
      .source("source", "kafka-json", "old-topic-param" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")

    val deployedScenarioData = DeployedScenarioData(ProcessVersion.empty.copy(processName = name), DeploymentData.empty, scenarioWithIncompatibleParameters)
    val FixtureParam(manager, _, _, _) = prepareFixture(inputTopic, outputTopic, List(deployedScenarioData))

    manager.findJobStatus(name).futureValue.map(_.status) should matchPattern {
      case Some(DetailedFailedStateStatus(msg)) if msg.contains("topic") =>
    }
  }

  test("Deploy scenario json incompatible with current component API should throw exception immediately instead of moving scenario to Failed state") {
    val fixture@FixtureParam(manager, _, inputTopic, outputTopic) = prepareFixture()
    val name = ProcessName("testName")
    // We simulate scenario json incompatible with component API by replacing parameter name with some other name
    val scenarioWithIncompatibleParameters = StreamingLiteScenarioBuilder
      .id(name.value)
      .source("source", "kafka-json", "old-topic-param" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")

    an [Exception] shouldBe thrownBy {
      fixture.deployScenario(scenarioWithIncompatibleParameters)
    }
  }

  test("Redeploys scenario") {
    val fixture@FixtureParam(manager, _, inputTopic, outputTopic) = prepareFixture()

    val name = ProcessName("testName")
    def scenarioForOutput(outputPrefix: String) = StreamingLiteScenarioBuilder
      .id(name.value)
      .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> s"{message: #input.message, prefix: '$outputPrefix'}")
    def message(input: String) = obj("message" -> fromString(input)).noSpaces
    def prefixMessage(prefix: String, message: String) = obj("message" -> fromString(message), "prefix" -> fromString(prefix))


    fixture.deployScenario(scenarioForOutput("start"))


    kafkaClient.sendMessage(inputTopic, message("1")).futureValue

    val consumer = kafkaClient.createConsumer().consumeWithJson(outputTopic)
    consumer.head shouldBe prefixMessage("start", "1")

    fixture.deployScenario(scenarioForOutput("next"))

    eventually {
      manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)
    }

    kafkaClient.sendMessage(inputTopic, message("2")).futureValue
    consumer.take(2) shouldBe List(prefixMessage("start", "1"), prefixMessage("next", "2"))

    kafkaClient.sendMessage(inputTopic, message("3")).futureValue
    consumer.take(3) shouldBe List(prefixMessage("start", "1"), prefixMessage("next" , "2"),
      prefixMessage("next", "3"))

    manager.cancel(name, User("a", "b")).futureValue

    manager.findJobStatus(name).futureValue shouldBe None
  }

  test("Performs test from file") {
    val fixture@FixtureParam(manager, modelData, inputTopic, outputTopic) = prepareFixture()

    def message(input: String) = obj("message" -> fromString(input)).noSpaces

    val name = ProcessName("testName")
    val scenario = StreamingLiteScenarioBuilder
      .id(name.value)
      .parallelism(1)
      .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'",
        "value" -> s"{message: #input.message, other: '1'}")

    kafkaClient.sendMessage(inputTopic, message("1")).futureValue
    kafkaClient.sendMessage(inputTopic, message("2")).futureValue

    val testData = TestData(new ModelDataTestInfoProvider(modelData).generateTestData(scenario.metaData,
        scenario.roots.head.data.asInstanceOf[Source], 2).get, 2)

    val results = wrapInFailingLoader {
      manager.test(name, ProcessMarshaller.toJson(ProcessCanonizer.canonize(scenario)).noSpaces, testData, identity[Any]).futureValue
    }
    results.nodeResults("sink") should have length 2
    val idGenerator = IncContextIdGenerator.withProcessIdNodeIdPrefix(scenario.metaData, "source")
    val invocationResults = results.invocationResults("sink")
    invocationResults.toSet shouldBe Set(
      ExpressionInvocationResult(idGenerator.nextContextId(), "value", Map("message" -> "1", "other" -> "1").asJava),
      ExpressionInvocationResult(idGenerator.nextContextId(), "value", Map("message" -> "2", "other" -> "1").asJava)
    )

  }

}
