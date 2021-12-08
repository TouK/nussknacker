package pl.touk.nussknacker.streaming.embedded

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.circe.Json
import io.circe.Json.{fromString, obj}
import org.scalatest.{Matchers, Outcome, fixture}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, DeploymentManager, GraphProcess, User}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils.richConsumer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.test.PatientScalaFutures

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits._

class EmbeddedDeploymentManagerTest extends fixture.FunSuite with KafkaSpec with Matchers with PatientScalaFutures {

  case class FixtureParam(deploymentManager: DeploymentManager, inputTopic: String, outputTopic: String) {
    def deployScenario(scenario: EspProcess): Unit = {
      val deploymentData = GraphProcess(ProcessMarshaller.toJson(ProcessCanonizer.canonize(scenario)).spaces2)
      val version = ProcessVersion.empty.copy(processName = ProcessName(scenario.id))
      deploymentManager.deploy(version, DeploymentData.empty, deploymentData, None).futureValue
    }
  }

  def withFixture(test: OneArgTest): Outcome = {
    val configToUse = config
      .withValue("auto.offset.reset", fromAnyRef("earliest"))
      .withValue("exceptionHandlingConfig.topic", fromAnyRef("errors"))
      .withValue("components.kafka.enabled", fromAnyRef(true))

    val modelData = LocalModelData(configToUse, new EmptyProcessConfigCreator)
    val manager = new EmbeddedDeploymentManager(modelData, ConfigFactory.empty(),
      (_: ProcessVersion, _: Throwable) => throw new AssertionError("Should not happen..."))
    val inputTopic = s"input-${UUID.randomUUID().toString}"
    val outputTopic = s"output-${UUID.randomUUID().toString}"

    withFixture(test.toNoArgTest(FixtureParam(manager, inputTopic, outputTopic)))
  }


  test("Deploys scenario and cancels") { fixture =>
    val FixtureParam(manager, inputTopic, outputTopic) = fixture

    val name = ProcessName("testName")
    val scenario = EspProcessBuilder
      .id(name.value)
      .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")

    fixture.deployScenario(scenario)

    manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)

    val input = obj("key" -> fromString("dummy"))
    kafkaClient.sendMessage(inputTopic, input.noSpaces).futureValue
    kafkaClient.createConsumer().consumeWithJson(outputTopic).head shouldBe input

    manager.cancel(name, User("a", "b")).futureValue

    manager.findJobStatus(name).futureValue shouldBe None
  }

  test("Redeploys scenario") { fixture =>
    val FixtureParam(manager, inputTopic, outputTopic) = fixture

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


}
