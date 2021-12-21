package pl.touk.nussknacker.k8s.manager

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.tags.Network
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, GraphProcess}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.ExecutionContext.Implicits.global

// we use this tag to mark tests using external dependencies
@Network
class K8sDeploymentManagerProviderTest extends FunSuite with Matchers with PatientScalaFutures with OptionValues {

  private implicit val system: ActorSystem = ActorSystem()

  private val dockerTag = sys.env.getOrElse("dockerTagName", BuildInfo.version)

  test("deployment of ping-pong") {
    val manager = prepareManager
    val scenario = EspProcessBuilder
      .id("fooScenario")
      .source("source", "kafka-json", "topic" -> s"'fooInputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'fooOutputTopic'", "value" -> "#input")
    val scenarioJson = GraphProcess(ProcessMarshaller.toJson(ProcessCanonizer.canonize(scenario)).spaces2)
    val deployResult = manager.deploy(ProcessVersion.empty.copy(processName = ProcessName(scenario.id)), DeploymentData.empty, scenarioJson, None).futureValue
    deployResult.value.value should not be empty
  }

  private def prepareManager = {
    val modelData = LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator)
    K8sDeploymentManager(modelData, ConfigFactory.empty.withValue("dockerImageTag", fromAnyRef(dockerTag)))
  }

}
