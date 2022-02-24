package pl.touk.nussknacker.k8s.manager

import akka.actor.ActorSystem
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable, fromMap}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Inspectors.forAll
import org.scalatest._
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.StreamingLiteScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.requirementForName
import pl.touk.nussknacker.test.ExtremelyPatientScalaFutures
import skuber.LabelSelector.dsl._
import skuber.Resource.Quantity
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.{ConfigMap, EnvVar, LabelSelector, ListResource, Pod, k8sInit}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.reflectiveCalls
import scala.util.Random

// we use this tag to mark tests using external dependencies
@Network
class K8sDeploymentManagerProviderTest extends FunSuite with Matchers with ExtremelyPatientScalaFutures with OptionValues with LazyLogging with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem()
  private lazy val k8s = k8sInit
  private lazy val kafka = new KafkaK8sSupport(k8s)
  private val dockerTag = sys.env.getOrElse("dockerTagName", BuildInfo.version)

  test("deployment of ping-pong") {
    //we append random to make it easier to test with reused kafka deployment
    val seed = new Random().nextInt()
    val input = s"ping-$seed"
    val output = s"pong-$seed"
    kafka.createTopic(input)
    kafka.createTopic(output)

    val manager = prepareManager()
    val scenario = StreamingLiteScenarioBuilder
      .id("foo scenario \u2620")
      .source("source", "kafka-json", "topic" -> s"'$input'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$output'", "value" -> "#input")
    logger.info(s"Running test on ${scenario.id} $input - $output")

    val version = ProcessVersion(VersionId(11), ProcessName(scenario.id), ProcessId(1234), "testUser", Some(22))
    manager.deploy(version, DeploymentData.empty, scenario.toCanonicalProcess, None).futureValue

    eventually {
      val state = manager.findJobStatus(version.processName).futureValue
      state.flatMap(_.version) shouldBe Some(version)
      state.map(_.status) shouldBe Some(SimpleStateStatus.Running)
    }
    val message = """{"message":"Nussknacker!"}"""
    kafka.sendToTopic(input, message)
    kafka.readFromTopic(output, 1) shouldBe List(message)

    manager.cancel(version.processName, DeploymentData.systemUser).futureValue

    eventually {
      manager.findJobStatus(version.processName).futureValue shouldBe None
    }

    //should not fail
    cancelAndAssertCleanupUp(manager, version)
  }

  test("redeployment of ping-pong") {
    //we append random to make it easier to test with reused kafka deployment
    val seed = new Random().nextInt()
    val input = s"ping-$seed"
    val output = s"pong-$seed"
    kafka.createTopic(input)
    kafka.createTopic(output)

    val manager = prepareManager()

    def deployScenario(version: Int) = {
      val scenario = StreamingLiteScenarioBuilder.id("foo scenario \u2620")
        .source("source", "kafka-json", "topic" -> s"'$input'")
        .emptySink("sink", "kafka-json", "topic" -> s"'$output'", "value" -> s"{ original: #input, version: $version }")

      val pversion = ProcessVersion(VersionId(version), ProcessName(scenario.id), ProcessId(1234), "testUser", Some(22))
      manager.deploy(pversion, DeploymentData.empty, scenario.toCanonicalProcess, None).futureValue
      pversion
    }

    def waitForRunning(version: ProcessVersion) = {
      eventually {
        val state = manager.findJobStatus(version.processName).futureValue
        state.flatMap(_.version) shouldBe Some(version)
        state.map(_.status) shouldBe Some(SimpleStateStatus.Running)
      }
    }

    val message = """{"message":"Nussknacker!"}"""

    def messageForVersion(version: Int) = s"""{"original":$message,"version":$version}"""

    val version1 = deployScenario(1)
    waitForRunning(version1)

    kafka.sendToTopic(input, message)
    kafka.readFromTopic(output, 1) shouldBe List(messageForVersion(1))

    val version2 = deployScenario(2)
    waitForRunning(version2)

    kafka.sendToTopic(input, message)
    kafka.readFromTopic(output, 2) shouldBe List(messageForVersion(1), messageForVersion(2))

    cancelAndAssertCleanupUp(manager, version2)
  }

  test("should deploy scenario with env, resources and replicas count from k8sDeploymentSpecConfig") {
    //we append random to make it easier to test with reused kafka deployment
    val seed = new Random().nextInt()
    val input = s"ping-$seed"
    val output = s"pong-$seed"
    kafka.createTopic(input)
    kafka.createTopic(output)

    val manager = prepareManager(deployConfig =
      deployConfig
        .withValue("k8sDeploymentConfig.spec.replicas", fromAnyRef(3))
        .withValue("k8sDeploymentConfig.spec.template.spec.containers",
          fromIterable(List(
            fromMap(Map(
              "name" -> "runtime",
              "image" -> s"touk/nussknacker-lite-kafka-runtime:${dockerTag}",
              "env" -> fromIterable(List(
                fromMap(
                  Map(
                    "name" -> "ENV_VARIABLE",
                    "value" -> "VALUE"
                  ).asJava
                )
              ).asJava),
              "resources"-> fromMap(
                Map(
                  "requests" -> fromMap(Map("memory"-> "256Mi", "cpu"-> "20m").asJava),
                  "limits" -> fromMap(Map("memory"-> "256Mi", "cpu"-> "20m").asJava)
                ).asJava
              )
            ).asJava)
          ).asJava)
        )
    )
    val scenario = StreamingLiteScenarioBuilder
      .id("foo scenario \u2620")
      .source("source", "kafka-json", "topic" -> s"'$input'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$output'", "value" -> "#input")
    logger.info(s"Running test on ${scenario.id} $input - $output")

    val version = ProcessVersion(VersionId(11), ProcessName(scenario.id), ProcessId(1234), "testUser", Some(22))
    manager.deploy(version, DeploymentData.empty, scenario.toCanonicalProcess, None).futureValue

    eventually {
      val state = manager.findJobStatus(version.processName).futureValue
      state.flatMap(_.version) shouldBe Some(version)
      state.map(_.status) shouldBe Some(SimpleStateStatus.Running)
    }

    eventually {
      val pods = k8s.listSelected[ListResource[Pod]](requirementForName(version.processName)).futureValue.items
      pods.size shouldBe 3
      forAll(pods.head.spec.get.containers) { container =>
        container.resources shouldBe Some(
          skuber.Resource.Requirements(
            limits = Map("cpu" -> Quantity("20m"), "memory" -> Quantity("256Mi")),
            requests = Map("cpu" -> Quantity("20m"), "memory" -> Quantity("256Mi"))
          ))
        container.env should contain (EnvVar("ENV_VARIABLE", EnvVar.StringValue("VALUE")))
      }
    }

    manager.cancel(version.processName, DeploymentData.systemUser).futureValue

    eventually {
      manager.findJobStatus(version.processName).futureValue shouldBe None
    }

    cancelAndAssertCleanupUp(manager, version)
  }

  override protected def beforeAll(): Unit = {
    //cleanup just in case...
    cleanup()
    kafka.start()
  }

  private def cleanup(): Unit = {
    val selector = LabelSelector(K8sDeploymentManager.scenarioNameLabel)
    Future.sequence(List(
      k8s.deleteAllSelected[ListResource[Deployment]](selector),
      k8s.deleteAllSelected[ListResource[ConfigMap]](selector),
    )).futureValue
    assertNoGarbageLeft()
    kafka.stop()
  }

  private def assertNoGarbageLeft(): Assertion = {
    val selector = LabelSelector(K8sDeploymentManager.scenarioNameLabel)
    eventually {
      k8s.listSelected[ListResource[Deployment]](selector).futureValue.items shouldBe Nil
      k8s.listSelected[ListResource[ConfigMap]](selector).futureValue.items shouldBe Nil
    }
  }

  override protected def afterAll(): Unit = {
    cleanup()
  }

  private def cancelAndAssertCleanupUp(manager: K8sDeploymentManager, version: ProcessVersion) = {
    manager.cancel(version.processName, DeploymentData.systemUser).futureValue
    eventually {
      manager.findJobStatus(version.processName).futureValue shouldBe None
    }
    assertNoGarbageLeft()
  }

  val deployConfig: Config = ConfigFactory.empty
    .withValue("dockerImageTag", fromAnyRef(dockerTag))
    .withValue("k8sDeploymentSpecConfig.replicas", fromAnyRef(3))
    .withValue("configExecutionOverrides.modelConfig.kafka.kafkaAddress", fromAnyRef(s"${KafkaK8sSupport.kafkaService}:9092"))
  val modelData: LocalModelData = LocalModelData(ConfigFactory.empty
    //e.g. when we want to run Designer locally with some proxy?
    .withValue("kafka.kafkaAddress", fromAnyRef("localhost:19092"))
    .withValue("exceptionHandlingConfig.topic", fromAnyRef("errors")), new EmptyProcessConfigCreator)

  def prepareManager(modelData: LocalModelData = modelData, deployConfig: Config = deployConfig): K8sDeploymentManager = {
    K8sDeploymentManager(modelData, deployConfig)
  }

}
