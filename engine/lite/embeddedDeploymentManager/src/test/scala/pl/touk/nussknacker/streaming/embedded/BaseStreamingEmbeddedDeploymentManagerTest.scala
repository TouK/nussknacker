package pl.touk.nussknacker.streaming.embedded

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromMap}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeployedScenarioData, DeploymentManager, ProcessingTypeDeploymentServiceStub}
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessName}
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.embedded.{EmbeddedDeploymentManager, StreamingDeploymentStrategy}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.test.{FailingContextClassloader, VeryPatientScalaFutures}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits._
import scala.jdk.CollectionConverters.mapAsJavaMapConverter

trait BaseStreamingEmbeddedDeploymentManagerTest extends FunSuite with KafkaSpec with Matchers with VeryPatientScalaFutures {

  case class FixtureParam(deploymentManager: DeploymentManager, modelData: ModelData, inputTopic: String, outputTopic: String) {
    def deployScenario(scenario: EspProcess): Unit = {
      val version = ProcessVersion.empty.copy(processName = ProcessName(scenario.id))
      deploymentManager.deploy(version, DeploymentData.empty, scenario.toCanonicalProcess, None).futureValue
    }
  }

  protected def generateInputTopicName = s"input-${UUID.randomUUID().toString}"

  protected def generateOutputTopicName = s"input-${UUID.randomUUID().toString}"

  protected def wrapInFailingLoader[T] = ThreadUtils.withThisAsContextClassLoader[T](new FailingContextClassloader) _

  protected def prepareFixture(inputTopic: String = generateInputTopicName, outputTopic: String = generateOutputTopicName,
                               initiallyDeployedScenarios: List[DeployedScenarioData] = List.empty): FixtureParam = {
    val configToUse = config
      .withValue("auto.offset.reset", fromAnyRef("earliest"))
      .withValue("exceptionHandlingConfig.topic", fromAnyRef("errors"))
      .withValue("components.kafka.enabled", fromAnyRef(true))
      .withValue("kafka.kafkaProperties", fromMap(Map[String, Any](
        //        This timeout controls how long the kafka producer initialization in pl.touk.nussknacker.engine.lite.kafka.KafkaSingleScenarioTaskRun.init.
        //        So it has to be set to a reasonably low value for the restarting test to finish before ScalaFutures patience runs out.
        "max.block.ms" -> 2000,
        "default.api.timeout.ms" -> 2000
      ).asJava))

    val modelData = LocalModelData(configToUse, new EmptyProcessConfigCreator)
    val deploymentService = new ProcessingTypeDeploymentServiceStub(initiallyDeployedScenarios)
    wrapInFailingLoader {
      val manager = new EmbeddedDeploymentManager(modelData, ConfigFactory.empty(), deploymentService, new StreamingDeploymentStrategy {
        override protected def handleUnexpectedError(version: ProcessVersion, throwable: Throwable): Unit = throw new AssertionError("Should not happen...")
      })
      FixtureParam(manager, modelData, inputTopic, outputTopic)
    }
  }

}
