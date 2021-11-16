package pl.touk.nussknacker.streaming.embedded

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, GraphProcess, User}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils.richConsumer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.test.PatientScalaFutures

import java.lang.Thread.UncaughtExceptionHandler
import scala.concurrent.ExecutionContext.Implicits._

class EmbeddedDeploymentManagerTest extends FunSuite with KafkaSpec with Matchers with PatientScalaFutures {

  test("Deploys scenario and cancels") {

    val inputTopic = "input"
    val outputTopic = "output"

    val configToUse = config
      .withValue("auto.offset.reset", fromAnyRef("earliest"))
      .withValue("kafka.kafkaProperties.retries", fromAnyRef("1"))
      .withValue("exceptionHandlingConfig.topic", fromAnyRef("errors"))

    val modelData = LocalModelData(configToUse, new EmptyProcessConfigCreator)
    val manager = new EmbeddedDeploymentManager(modelData, ConfigFactory.empty(), new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = throw new AssertionError("Should not happen...")
    })

    val name = ProcessName("testName")
    val scenario = EspProcessBuilder
      .id(name.value)
      .exceptionHandler()
      .source("source", "source", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "sink", "topic" -> s"'$outputTopic'", "value" -> "#input")

    val deploymentData = GraphProcess(ProcessMarshaller.toJson(ProcessCanonizer.canonize(scenario)).spaces2)
    val version = ProcessVersion.empty.copy(processName = ProcessName(scenario.id))
    manager.deploy(version, DeploymentData.empty, deploymentData, None).futureValue

    manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)

    kafkaClient.sendMessage(inputTopic, "dummy").futureValue
    kafkaClient.createConsumer().consume(outputTopic).head

    manager.cancel(name, User("a", "b")).futureValue

    manager.findJobStatus(name).futureValue shouldBe None
  }

}
