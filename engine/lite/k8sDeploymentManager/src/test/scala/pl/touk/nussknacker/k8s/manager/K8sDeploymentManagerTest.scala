package pl.touk.nussknacker.k8s.manager

import akka.actor.ActorSystem
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable, fromMap}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Inspectors.forAll
import org.scalatest.{Assertion, BeforeAndAfterAll, OptionValues}
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.requirementForName
import pl.touk.nussknacker.k8s.manager.K8sPodsResourceQuotaChecker.ResourceQuotaExceededException
import pl.touk.nussknacker.test.{AvailablePortFinder, ExtremelyPatientScalaFutures}
import skuber.Container.Port
import skuber.LabelSelector.dsl._
import skuber.Resource.{Quantity, Quota}
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.{ConfigMap, EnvVar, LabelSelector, ListResource, ObjectMeta, Pod, Resource, Secret, Volume, k8sInit}
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend, _}

import java.io.File
import java.nio.file.Files
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.reflectiveCalls
import scala.util.Random
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.net.Socket

// we use this tag to mark tests using external dependencies
@Network
class K8sDeploymentManagerTest extends AnyFunSuite with Matchers with ExtremelyPatientScalaFutures with OptionValues with LazyLogging with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem()
  private lazy val k8s = k8sInit
  private lazy val kafka = new KafkaK8sSupport(k8s)
  private val dockerTag = sys.env.getOrElse("dockerTagName", BuildInfo.version)
  private implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

  test("deployment of kafka ping-pong") {
    val f = createKafkaFixture()
    f.withRunningScenario {
      val message = """{"message":"Nussknacker!"}"""
      kafka.sendToTopic(f.inputTopic, message)
      kafka.readFromTopic(f.outputTopic, 1) shouldBe List(message)
    }
  }

  test("deployment of req-resp ping-pong") {
    val f = createReqRespFixture()
    f.withRunningScenario {
      val pingMessage = """{"ping":"Nussknacker!"}"""
      val pongMessage = """{"pong":"Nussknacker!"}"""
      // TODO: replace with service invocation
      f.withPortForwarded(f.port) {
        val request = basicRequest.post(uri"http://localhost".port(f.port).path("scenario", f.scenario.id))
        request.body(pingMessage).send().body shouldBe Right(pongMessage)
      }
    }
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
      val scenario = ScenarioBuilder.streamingLite("foo scenario \u2620")
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

    cancelAndAssertCleanup(manager, version2)
  }

  test("should redeploy during deploy") {
    //we append random to make it easier to test with reused kafka deployment
    val seed = new Random().nextInt()
    val inputTopic = s"in-$seed"
    val outputTopic = s"out1-$seed"
    val otherOutputTopic = s"out2-$seed"
    List(inputTopic, outputTopic, otherOutputTopic).foreach(kafka.createTopic)

    val manager = prepareManager()

    def scenarioWithOutputTo(topicName: String) = ScenarioBuilder
      .streamingLite("foo scenario \u2620")
      .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$topicName'", "value" -> "#input")

    def waitFor(version: ProcessVersion) = {
      class InStateAssertionHelper {
        def inState(stateStatus: StateStatus): Assertion = eventually {
          val state = manager.findJobStatus(version.processName).futureValue
          state.flatMap(_.version) shouldBe Some(version)
          state.map(_.status) shouldBe Some(stateStatus)
        }
      }
      new InStateAssertionHelper()
    }

    val scenario = scenarioWithOutputTo(outputTopic)
    val version = ProcessVersion(VersionId(11), ProcessName(scenario.id), ProcessId(1234), "testUser", Some(22))

    val message = """{"message":"Nussknacker!"}"""
    kafka.sendToTopic(inputTopic, message)

    val otherVersion = version.copy(versionId = VersionId(12), modelVersion = Some(23))
    val otherScenario = scenarioWithOutputTo(otherOutputTopic)
    manager.deploy(version, DeploymentData.empty, scenario.toCanonicalProcess, None).futureValue
    waitFor(version).inState(SimpleStateStatus.DuringDeploy)

    val oldPod = k8s.listSelected[ListResource[Pod]](requirementForName(version.processName)).futureValue.items.head

    manager.deploy(otherVersion, DeploymentData.empty, otherScenario.toCanonicalProcess, None).futureValue

    var statuses: List[StateStatus] = Nil
    // wait until new pod arrives..
    eventually {
      val newPod = k8s.listSelected[ListResource[Pod]](requirementForName(version.processName)).futureValue.items.head
      if (newPod.metadata.name == oldPod.metadata.name) {
        statuses = statuses ::: manager.findJobStatus(otherVersion.processName).futureValue.get.status :: Nil
      }
      newPod.metadata.name should not be oldPod.metadata.name
    }
    //..and make sure scenario status was never Running to this point
    statuses should contain only SimpleStateStatus.DuringDeploy

    waitFor(otherVersion).inState(SimpleStateStatus.Running)
    kafka.readFromTopic(otherOutputTopic, 1) shouldBe List(message)
    manager.cancel(otherVersion.processName, DeploymentData.systemUser).futureValue
    eventually {
      manager.findJobStatus(version.processName).futureValue shouldBe None
    }
    //should not fail
    cancelAndAssertCleanup(manager, version)
  }


  test("should deploy scenario with env, resources and replicas count from k8sDeploymentSpecConfig") {
    val f = createKafkaFixture(
      deployConfig = kafkaDeployConfig
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
              "resources" -> fromMap(
                Map(
                  "requests" -> fromMap(Map("memory" -> "256Mi", "cpu" -> "20m").asJava),
                  "limits" -> fromMap(Map("memory" -> "256Mi", "cpu" -> "20m").asJava)
                ).asJava
              )
            ).asJava)
          ).asJava)
        )
    )
    f.withRunningScenario {
      eventually {
        val pods = k8s.listSelected[ListResource[Pod]](requirementForName(f.version.processName)).futureValue.items
        pods.size shouldBe 3
        forAll(pods.head.spec.get.containers) { container =>
          container.resources shouldBe Some(
            skuber.Resource.Requirements(
              limits = Map("cpu" -> Quantity("20m"), "memory" -> Quantity("256Mi")),
              requests = Map("cpu" -> Quantity("20m"), "memory" -> Quantity("256Mi"))
            ))
          container.env should contain(EnvVar("ENV_VARIABLE", EnvVar.StringValue("VALUE")))
        }
      }
    }
  }

  test("should deploy scenario with custom logging configuration") {
    val f = createKafkaFixture()

    def withManager(manager: K8sDeploymentManager)(action: ProcessVersion => Unit): Unit = {
      val version = ProcessVersion(VersionId(11), ProcessName(f.scenario.id), ProcessId(1234), "testUser", Some(22))
      manager.deploy(version, DeploymentData.empty, f.scenario.toCanonicalProcess, None).futureValue

      action(version)
      cancelAndAssertCleanup(manager, version)
    }

    val customLogger = "test.passing.logback.conf"
    val logbackFile = {
      val tempFile = Files.createTempFile("test-logback", ".xml")
      Files.write(tempFile,
        s"""
           |<configuration scan="true" scanPeriod="5 seconds">
           |    <logger name="$customLogger" level="WARN"/>
           |</configuration>
           |""".stripMargin.getBytes)
      tempFile.toFile
    }
    val manager: K8sDeploymentManager = prepareManager(deployConfig =
      kafkaDeployConfig
        .withValue(
          "logbackConfigPath", fromAnyRef(logbackFile.toString)
        )
    )

    withManager(manager) { version =>
      eventually {
        val cm = k8s.listSelected[ListResource[ConfigMap]](requirementForName(version.processName)).futureValue.items.find {
          _.data.isDefinedAt("logback.xml")
        }.head
        cm.data("logback.xml").contains(customLogger) shouldBe true
      }
    }

    withManager(prepareManager()) { version =>
      eventually {
        val cm = k8s.listSelected[ListResource[ConfigMap]](requirementForName(version.processName)).futureValue.items.find {
          _.data.isDefinedAt("logback.xml")
        }.head
        cm.data("logback.xml").contains(customLogger) shouldBe false
      }
    }
  }

  test("should deploy scenarios with common logging conf") {
    val configMapName = "test" + new Random().nextInt(1000000)
    val f = createKafkaFixture(deployConfig = kafkaDeployConfig.withValue("commonConfigMapForLogback", fromAnyRef(configMapName)))

    f.withRunningScenario {
      //check if cm exists
      k8s.list[ListResource[ConfigMap]].futureValue.items.exists(_.name == configMapName) shouldBe true

      //check if cm is actually mounted
      val pod = k8s.listSelected[ListResource[Pod]](requirementForName(f.version.processName)).futureValue.items.head
      pod.spec.get.volumes.exists(_.source match {
        case Volume.ConfigMapVolumeSource(`configMapName`, _, _, _) => true
        case _ => false
      }) shouldBe true
    }
    // check that after cancelling scenario CM is still there
    k8s.list[ListResource[ConfigMap]].futureValue.items.exists(_.name == configMapName) shouldBe true

    //cleanup
    k8s.delete[ConfigMap](configMapName)
  }

  test("should deploy within specified resource quota") {
    val f = createKafkaFixture()
    k8s.create(Quota(metadata = ObjectMeta(name = "nu-pods-limit"), spec = Some(Quota.Spec(hard = Map[String, Quantity]("pods" -> Quantity("2"))))))
    f.withRunningScenario(())
    k8s.delete[Resource.Quota]("nu-pods-limit").futureValue
  }

  test("should not deploy when resource quota exceeded") {
    val f = createKafkaFixture()
    k8s.create(Quota(metadata = ObjectMeta(name = "nu-pods-limit"), spec = Some(Quota.Spec(hard = Map[String, Quantity]("pods" -> Quantity("1"))))))

    f.manager.validate(f.version, DeploymentData.empty, f.scenario.toCanonicalProcess).failed.futureValue shouldEqual
      ResourceQuotaExceededException("Cluster is full. Release some cluster resources.")

    cancelAndAssertCleanup(f.manager, f.version)
    k8s.delete[Resource.Quota]("nu-pods-limit").futureValue
  }

  test("should expose prometheus metrics") {
    val port = AvailablePortFinder.findAvailablePorts(1).head
    val f = createKafkaFixture(deployConfig = kafkaDeployConfig
      .withValue("k8sDeploymentConfig.spec.template.spec.containers",
        fromIterable(List(
          fromMap(Map(
            "name" -> "runtime",
            "env" -> fromIterable(List(fromMap(Map(
              "name" -> "PROMETHEUS_METRICS_PORT",
              "value" -> s"$port"
            ).asJava
            )
            ).asJava),
            "ports" -> fromIterable(List(fromMap(Map(
              "name" -> "prometheus",
              "containerPort" -> port,
              "protocol" -> "TCP"
            ).asJava)
            ).asJava
            )
          ).asJava)
        ).asJava)
    ))

    f.withRunningScenario {
      val pod = k8s.listSelected[ListResource[Pod]](requirementForName(f.version.processName)).futureValue.items.head
      pod.spec.get.containers.head.ports should contain theSameElementsAs List(Port(port, name = "prometheus" ))

      f.withPortForwarded(port) {
        eventually {
          basicRequest.get(uri"http://localhost:$port").send().body.right.get.contains("jvm_memory_bytes_committed") shouldBe true
        }
      }
    }
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
      k8s.deleteAllSelected[ListResource[Secret]](selector),
      k8s.delete[Resource.Quota]("nu-pods-limit")
    )).futureValue
    assertNoGarbageLeft()
    kafka.stop()
  }

  private def assertNoGarbageLeft(): Assertion = {
    val selector = LabelSelector(K8sDeploymentManager.scenarioNameLabel)
    eventually {
      k8s.listSelected[ListResource[Deployment]](selector).futureValue.items shouldBe Nil
      k8s.listSelected[ListResource[ConfigMap]](selector).futureValue.items shouldBe Nil
      k8s.listSelected[ListResource[Secret]](selector).futureValue.items shouldBe Nil
      k8s.listSelected[ListResource[Pod]](selector).futureValue.items shouldBe Nil
    }
  }

  override protected def afterAll(): Unit = {
    cleanup()
  }

  private def cancelAndAssertCleanup(manager: K8sDeploymentManager, version: ProcessVersion) = {
    manager.cancel(version.processName, DeploymentData.systemUser).futureValue
    eventually {
      manager.findJobStatus(version.processName).futureValue shouldBe None
    }
    assertNoGarbageLeft()
  }

  private val kafkaDeployConfig: Config = ConfigFactory.empty
    .withValue("dockerImageTag", fromAnyRef(dockerTag))
    .withValue("k8sDeploymentSpecConfig.replicas", fromAnyRef(3))
    .withValue("configExecutionOverrides.modelConfig.kafka.kafkaAddress", fromAnyRef(s"${KafkaK8sSupport.kafkaService}:9092"))

  private def reqRespDeployConfig(port: Int): Config = ConfigFactory.empty
    .withValue("dockerImageTag", fromAnyRef(dockerTag))
    .withValue("k8sDeploymentSpecConfig.replicas", fromAnyRef(1))
    .withValue("configExecutionOverrides.http.port", fromAnyRef(port))

  private val modelData: LocalModelData = LocalModelData(ConfigFactory.empty
    //e.g. when we want to run Designer locally with some proxy?
    .withValue("kafka.kafkaAddress", fromAnyRef("localhost:19092"))
    .withValue("kafka.lowLevelComponentsEnabled", fromAnyRef(true))
    .withValue("kafka.kafkaProperties.\"auto.offset.reset\"", fromAnyRef("earliest"))
    .withValue("exceptionHandlingConfig.topic", fromAnyRef("errors")), new EmptyProcessConfigCreator)

  private def prepareManager(modelData: LocalModelData = modelData, deployConfig: Config = kafkaDeployConfig): K8sDeploymentManager = {
    K8sDeploymentManager(modelData, deployConfig)
  }

  private def createKafkaFixture(modelData: LocalModelData = modelData, deployConfig: Config = kafkaDeployConfig) = {
    val seed = new Random().nextInt()
    val input = s"ping-$seed"
    val output = s"pong-$seed"
    val manager = prepareManager(modelData, deployConfig)
    val scenario = ScenarioBuilder
      .streamingLite("foo scenario \u2620")
      .source("source", "kafka-json", "topic" -> s"'$input'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$output'", "value" -> "#input")
    logger.info(s"Running kafka test on ${scenario.id} $input - $output")
    val version = ProcessVersion(VersionId(11), ProcessName(scenario.id), ProcessId(1234), "testUser", Some(22))
    new KafkaTestFixture(inputTopic = input, outputTopic = output, manager = manager, scenario = scenario, version = version)
  }

  // TODO: use req-resp model data
  private def createReqRespFixture(modelData: LocalModelData = modelData) = {
    val port = AvailablePortFinder.findAvailablePorts(1).head
    val deployConfig = reqRespDeployConfig(port)
    val manager = prepareManager(modelData, deployConfig)
    val pingSchema = """{
                       |  "type": "object",
                       |  "properties": {
                       |    "ping": { "type": "string" }
                       |  }
                       |}
                       |""".stripMargin

    val pongSchema = """{
                       |  "type": "object",
                       |  "properties": {
                       |    "pong": { "type": "string" }
                       |  }
                       |}
                       |""".stripMargin
    val scenario = ScenarioBuilder
      .requestResponse("reqresp-ping-pong")
      .additionalFields(properties = Map(
        "inputSchema" -> pingSchema,
        "outputSchema" -> pongSchema
      ))
      .source("source", "request")
      .emptySink("sink", "response", "pong" -> "#input.ping")
    logger.info(s"Running req-resp test on ${scenario.id}")
    val version = ProcessVersion(VersionId(11), ProcessName(scenario.id), ProcessId(1234), "testUser", Some(22))
    new ReqRespTestFixture(port = port, manager = manager, scenario = scenario, version = version)
  }

  private class KafkaTestFixture(val inputTopic: String,
                                 val outputTopic: String,
                                 manager: K8sDeploymentManager,
                                 scenario: EspProcess,
                                 version: ProcessVersion) extends TestFixture(manager, scenario, version) {
    override def withRunningScenario(action: => Unit): Assertion = {
      kafka.createTopic(inputTopic)
      kafka.createTopic(outputTopic)
      super.withRunningScenario(action)
    }
  }

  private class ReqRespTestFixture(val port: Int,
                                   manager: K8sDeploymentManager,
                                   scenario: EspProcess,
                                   version: ProcessVersion) extends TestFixture(manager, scenario, version)

  private class TestFixture(val manager: K8sDeploymentManager, val scenario: EspProcess, val version: ProcessVersion) {
    def withRunningScenario(action: => Unit) = {
      manager.deploy(version, DeploymentData.empty, scenario.toCanonicalProcess, None).futureValue
      eventually {
        val state = manager.findJobStatus(version.processName).futureValue
        state.flatMap(_.version) shouldBe Some(version)
        state.map(_.status) shouldBe Some(SimpleStateStatus.Running)
      }

      action

      manager.cancel(version.processName, DeploymentData.systemUser).futureValue
      eventually {
        manager.findJobStatus(version.processName).futureValue shouldBe None
      }
      //should not fail
      cancelAndAssertCleanup(manager, version)
    }

    def withPortForwarded(port:Int)(action: => Unit) = {
      val pod = k8s.listSelected[ListResource[Pod]](requirementForName(version.processName)).futureValue.items.head
      val portForward: Process = new ProcessBuilder("kubectl", "port-forward", s"${pod.name}", s"$port:$port")
        .directory(new File("/tmp"))
        .start()
      eventually {
        new Socket("localhost", port)
      }
      try {
        action
      } finally {
        portForward.destroy()
      }
    }
  }
}
