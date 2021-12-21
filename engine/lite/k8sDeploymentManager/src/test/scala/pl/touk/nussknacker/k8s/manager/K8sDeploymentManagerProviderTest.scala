package pl.touk.nussknacker.k8s.manager

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.tags.Network
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers, OptionValues}
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
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import skuber.Container.Terminated
import skuber.{Pod, k8sInit}

import scala.concurrent.ExecutionContext.Implicits.global

// we use this tag to mark tests using external dependencies
@Network
class K8sDeploymentManagerProviderTest extends FunSuite with Matchers with VeryPatientScalaFutures with OptionValues with LazyLogging with BeforeAndAfterAll {

  import skuber.json.format._

  private implicit val system: ActorSystem = ActorSystem()

  private val dockerTag = sys.env.getOrElse("dockerTagName", BuildInfo.version)

  private lazy val k8s = k8sInit

  test("deployment of ping-pong") {
    val manager = prepareManager
    val scenario = EspProcessBuilder
      .id("fooScenario")
      .source("source", "kafka-json", "topic" -> s"'fooInputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'fooOutputTopic'", "value" -> "#input")
    val scenarioJson = GraphProcess(ProcessMarshaller.toJson(ProcessCanonizer.canonize(scenario)).spaces2)
    manager.deploy(ProcessVersion.empty.copy(processName = ProcessName(scenario.id)), DeploymentData.empty, scenarioJson, None).futureValue

    // TODO: implement correct checking
    eventually {
      val podStatus = k8s.get[Pod]("runtime").futureValue.status.value
      val containerState = podStatus.containerStatuses.headOption.value.state.value
      logger.debug(s"Container state: $containerState")
      containerState should matchPattern {
        // is Terminated(error) instead of Waiting(ErrImagePull) which means that image was pulled
        case terminated: Terminated if terminated.reason.contains("Error") =>
      }
    }
  }

  // TODO: correct cleanup
  override protected def afterAll(): Unit = {
    k8s.delete[Pod]("runtime").futureValue
  }

  private def prepareManager = {
    val modelData = LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator)
    K8sDeploymentManager(modelData, ConfigFactory.empty.withValue("dockerImageTag", fromAnyRef(dockerTag)))
  }

}
