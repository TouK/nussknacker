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
import pl.touk.nussknacker.engine.build.StreamingLiteScenarioBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import skuber.LabelSelector.dsl._
import skuber.apps.v1.Deployment
import skuber.{ConfigMap, LabelSelector, ListResource, k8sInit}
import skuber.json.format._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.reflectiveCalls
import scala.util.Random

// we use this tag to mark tests using external dependencies
@Network
class K8sDeploymentManagerProviderTest extends FunSuite with Matchers with VeryPatientScalaFutures with OptionValues with LazyLogging with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem()

  private val dockerTag = sys.env.getOrElse("dockerTagName", BuildInfo.version)

  private lazy val k8s = k8sInit

  private lazy val kafka = new KafkaK8sSupport(k8s)

  test("deployment of ping-pong") {
    //we append random to make it easier to test with reused kafka deployment
    val seed = new Random().nextInt()
    val input = s"ping-$seed"
    val output = s"pong-$seed"
    kafka.createTopic(input)
    kafka.createTopic(output)

    logger.info(s"Running test on $input - $output")
    val manager = prepareManager
    val scenario = StreamingLiteScenarioBuilder
      .id("fooScenario")
      .source("source", "kafka-json", "topic" -> s"'$input'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$output'", "value" -> "#input")
    val scenarioJson = GraphProcess(ProcessMarshaller.toJson(ProcessCanonizer.canonize(scenario)).spaces2)
    val version = ProcessVersion.empty.copy(processName = ProcessName(scenario.id))
    manager.deploy(version, DeploymentData.empty, scenarioJson, None).futureValue

    eventually {
      val deploymentStatus = k8s.get[Deployment](manager.nameForVersion(version)).futureValue.status.value
      deploymentStatus.availableReplicas shouldBe 1
    }
    val message = """{"message":"Nussknacker!"}"""
    kafka.sendToTopic(input, message)
    kafka.readFromTopic(output, 1) shouldBe List(message)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    cleanup()
    kafka.start()
  }

  override protected def afterAll(): Unit = {
    cleanup()
  }

  private def cleanup(): Unit = {
    val selector = LabelSelector("nussknacker.io/scenario")
    Future.sequence(List(
      k8s.deleteAllSelected[ListResource[Deployment]](selector),
      k8s.deleteAllSelected[ListResource[ConfigMap]](selector),
    )).futureValue
    eventually {
      k8s.listSelected[ListResource[Deployment]](selector).futureValue.items shouldBe Nil
    }
    kafka.stop()
  }

  private def prepareManager = {
    val modelData = LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator)
    val deployConfig = ConfigFactory.empty
      .withValue("dockerImageTag", fromAnyRef(dockerTag))
      .withValue("env.KAFKA_ADDRESS", fromAnyRef(s"${KafkaK8sSupport.kafkaService}:9092"))
      .withValue("env.KAFKA_ERROR_TOPIC", fromAnyRef("errors"))
    K8sDeploymentManager(modelData, deployConfig)
  }

}
