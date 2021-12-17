package pl.touk.nussknacker.k8s.manager

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.FunSuite
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
import scala.concurrent.ExecutionContext.Implicits.global

class K8sDeploymentManagerProviderTest extends FunSuite {

  private val dockerTag = sys.env.getOrElse("dockerTagName", BuildInfo.version)

  test("deployment of ping-pong") {
    val manager = prepareManager
    val scenario = EspProcessBuilder
      .id("fooScenario")
      .source("source", "kafka-json", "topic" -> s"'fooInputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'fooOutputTopic'", "value" -> "#input")
    val scenarioJson = GraphProcess(ProcessMarshaller.toJson(ProcessCanonizer.canonize(scenario)).spaces2)
    manager.deploy(ProcessVersion.empty.copy(processName = ProcessName("foo")), DeploymentData.empty, scenarioJson, None)
  }

  private def prepareManager = {
    val modelData = LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator)
    K8sDeploymentManager(modelData, ConfigFactory.empty.withValue("dockerImageTag", fromAnyRef(dockerTag)))
  }
}
