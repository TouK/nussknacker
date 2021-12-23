package pl.touk.nussknacker.streaming.embedded

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromMap}
import io.circe.Json.{fromString, obj}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{ExpressionInvocationResult, TestData}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.runtimecontext.IncContextIdGenerator
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.definition.ModelDataTestInfoProvider
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.test.{FailingContextClassloader, VeryPatientScalaFutures}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits._
import scala.jdk.CollectionConverters.mapAsJavaMapConverter

class EmbeddedDeploymentManagerTest extends FunSuite with KafkaSpec with Matchers with VeryPatientScalaFutures {

  case class FixtureParam(deploymentManager: DeploymentManager, modelData: ModelData, inputTopic: String, outputTopic: String) {
    def deployScenario(scenario: EspProcess): Unit = {
      val deploymentData = GraphProcess(ProcessMarshaller.toJson(ProcessCanonizer.canonize(scenario)).spaces2)
      val version = ProcessVersion.empty.copy(processName = ProcessName(scenario.id))
      deploymentManager.deploy(version, DeploymentData.empty, deploymentData, None).futureValue
    }
  }

  private def generateInputTopicName = s"input-${UUID.randomUUID().toString}"

  private def generateOutputTopicName = s"input-${UUID.randomUUID().toString}"

  private def wrapInFailingLoader[T] = ThreadUtils.withThisAsContextClassLoader[T](new FailingContextClassloader) _

  private def prepareFixture(inputTopic: String = generateInputTopicName, outputTopic: String = generateOutputTopicName,
                             initiallyDeployedScenarios: List[DeployedScenarioData] = List.empty) = {
    val configToUse = config
      .withValue("auto.offset.reset", fromAnyRef("earliest"))
      .withValue("exceptionHandlingConfig.topic", fromAnyRef("errors"))
      .withValue("components.kafka.enabled", fromAnyRef(true))
      .withValue("kafka.kafkaProperties", fromMap(Map[String, Any](
//        This timeout controls how long the kafka producer initialization in pl.touk.nussknacker.engine.lite.kafka.KafkaSingleScenarioTaskRun.init.
//        So it has to be set to a reasonably low value for the restarting test to finish before ScalaFutures patience runs out.
        "max.block.ms" -> 1000,
        "default.api.timeout.ms" -> 1000
      ).asJava))

    val modelData = LocalModelData(configToUse, new EmptyProcessConfigCreator)
    val deploymentService = new ProcessingTypeDeploymentServiceStub(initiallyDeployedScenarios)
    wrapInFailingLoader {
      val manager = new EmbeddedDeploymentManager(modelData, ConfigFactory.empty(), deploymentService,
        (_: ProcessVersion, _: Throwable) => throw new AssertionError("Should not happen..."))
      FixtureParam(manager, modelData, inputTopic, outputTopic)
    }
  }


  test("Deploys scenario and cancels") {
    val fixture@FixtureParam(manager, _, inputTopic, outputTopic) = prepareFixture()

    val name = ProcessName("testName")
    val scenario = EspProcessBuilder
      .id(name.value)
      .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")

    wrapInFailingLoader {
      fixture.deployScenario(scenario)
    }

    manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)

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
    val scenario = EspProcessBuilder
      .id(name.value)
      .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")

    val deployedScenarioData = DeployedScenarioData(ProcessVersion.empty.copy(processName = name), DeploymentData.empty, scenario)
    val FixtureParam(manager, _, _, _) = prepareFixture(inputTopic, outputTopic, List(deployedScenarioData))

    manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)

    val input = obj("key" -> fromString("dummy"))
    kafkaClient.sendMessage(inputTopic, input.noSpaces).futureValue
    kafkaClient.createConsumer().consumeWithJson(outputTopic).head shouldBe input

    manager.cancel(name, User("a", "b")).futureValue

    manager.findJobStatus(name).futureValue shouldBe None
  }

  test("Set status to restarting when scenario fails and back to running when the problems are fixed") {
    val fixture@FixtureParam(manager, _, inputTopic, outputTopic) = prepareFixture()

    val name = ProcessName("testName")
    val scenario = EspProcessBuilder
      .id(name.value)
      .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")

    wrapInFailingLoader {
      fixture.deployScenario(scenario)
    }

    manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)

    kafkaZookeeperServer.kafkaServer.shutdown()
    kafkaZookeeperServer.kafkaServer.awaitShutdown()

    eventually {
      val jobStatus = manager.findJobStatus(name).futureValue
      jobStatus.map(_.status) shouldBe Some(EmbeddedStateStatus.Restarting)
      jobStatus.map(_.allowedActions).get should contain only (ProcessActionType.Cancel)
    }

    kafkaZookeeperServer.kafkaServer.startup()

    eventually {
      manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)
    }
  }

  test("Redeploys scenario") {
    val fixture@FixtureParam(manager, _, inputTopic, outputTopic) = prepareFixture()

    val name = ProcessName("testName")
    def scenarioForOutput(outputPrefix: String) = EspProcessBuilder
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
    manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)

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
    val scenario = EspProcessBuilder
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
