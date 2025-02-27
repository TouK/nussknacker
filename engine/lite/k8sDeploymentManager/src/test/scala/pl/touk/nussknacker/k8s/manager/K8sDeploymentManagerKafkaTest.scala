package pl.touk.nussknacker.k8s.manager

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable, fromMap}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Inspectors.forAll
import org.scalatest.OptionValues
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{
  DataFreshnessPolicy,
  DeploymentUpdateStrategy,
  DMCancelScenarioCommand,
  DMRunDeploymentCommand,
  DMValidateScenarioCommand
}
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.requirementForName
import pl.touk.nussknacker.k8s.manager.K8sPodsResourceQuotaChecker.ResourceQuotaExceededException
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, KafkaConfigProperties}
import skuber.{ConfigMap, EnvVar, ListResource, ObjectMeta, Pod, Resource, Volume}
import skuber.Container.Port
import skuber.LabelSelector.dsl._
import skuber.Resource.{Quantity, Quota}
import skuber.json.format._
import sttp.client3._

import java.nio.file.Files
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._
import scala.language.reflectiveCalls
import scala.util.Random

// we use this tag to mark tests using external dependencies
@Network
class K8sDeploymentManagerKafkaTest
    extends BaseK8sDeploymentManagerTest
    with OptionValues
    with EitherValuesDetailedMessage
    with LazyLogging {

  private implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
  private lazy val kafka                                    = new KafkaK8sSupport(k8s)

  test("deployment of kafka ping-pong") {
    val f = createKafkaFixture()
    f.withRunningScenario {
      val message = """{"message":"Nussknacker!"}"""
      kafka.sendToTopic(f.inputTopic, message)
      kafka.readFromTopic(f.outputTopic, 1) shouldBe List(message)
    }
  }

  test("redeployment of ping-pong") {
    // we append random to make it easier to test with reused kafka deployment
    val seed   = new Random().nextInt()
    val input  = s"ping-$seed"
    val output = s"pong-$seed"
    kafka.createTopic(input)
    kafka.createTopic(output)

    val manager = prepareManager()

    def deployScenario(version: Int) = {
      val scenario = ScenarioBuilder
        .streamingLite("foo scenario \u2620")
        .source("source", "kafka", "Topic" -> s"'$input'".spel, "Schema version" -> "'latest'".spel)
        .emptySink(
          "sink",
          "kafka",
          "Topic"                 -> s"'$output'".spel,
          "Schema version"        -> "'latest'".spel,
          "Key"                   -> "".spel,
          "Raw editor"            -> "true".spel,
          "Value validation mode" -> "'strict'".spel,
          "Value"                 -> s"{ original: #input, version: $version }".spel
        )

      val pversion =
        ProcessVersion(VersionId(version), scenario.name, ProcessId(1234), List.empty, "testUser", Some(22))
      manager
        .processCommand(
          DMRunDeploymentCommand(
            pversion,
            DeploymentData.empty,
            scenario,
            DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
            )
          )
        )
        .futureValue
      pversion
    }

    def waitForRunning(version: ProcessVersion) = {
      eventually {
        val state = manager.getScenarioDeploymentsStatuses(version.processName).map(_.value).futureValue
        state.flatMap(_.version) shouldBe List(version.versionId)
        state.map(_.status) shouldBe List(SimpleStateStatus.Running)
      }
    }

    val message = """{"message":"Nussknacker!"}"""

    def messageForVersion(version: Int) = s"""{"original":$message,"version":$version}"""

    kafka.createSchema(s"$input-value", defaultSchema)
    kafka.createSchema(
      s"$output-value",
      s"""{"type":"object","properties":{"original":$defaultSchema,"version":{"type":"number"}}}"""
    )

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

  test("should deploy scenario with env, resources and replicas count from k8sDeploymentConfig") {
    val runtimeContainerConfig = baseRuntimeContainerConfig
      .withValue(
        "env",
        fromIterable(
          List(
            fromMap(
              Map(
                "name"  -> "ENV_VARIABLE",
                "value" -> "VALUE"
              ).asJava
            )
          ).asJava
        )
      )
      .withValue(
        "resources",
        fromMap(
          Map(
            "requests" -> fromMap(Map("memory" -> "512Mi", "cpu" -> "1024m").asJava),
            "limits"   -> fromMap(Map("memory" -> "512Mi", "cpu" -> "1024m").asJava)
          ).asJava
        )
      )
      .root()
    val f = createKafkaFixture(
      deployConfig = kafkaDeployConfig
        .withValue("k8sDeploymentConfig.spec.replicas", fromAnyRef(2))
        .withValue(
          "k8sDeploymentConfig.spec.template.spec.containers",
          fromIterable(List(runtimeContainerConfig).asJava)
        )
    )
    f.withRunningScenario {
      eventually {
        val pods = k8s.listSelected[ListResource[Pod]](requirementForName(f.version.processName)).futureValue.items
        pods.size shouldBe 2
        forAll(pods.head.spec.get.containers) { container =>
          container.resources shouldBe Some(
            skuber.Resource.Requirements(
              limits = Map("cpu" -> Quantity("1024m"), "memory" -> Quantity("512Mi")),
              requests = Map("cpu" -> Quantity("1024m"), "memory" -> Quantity("512Mi"))
            )
          )
          container.env should contain(EnvVar("ENV_VARIABLE", EnvVar.StringValue("VALUE")))
        }
      }
    }
  }

  test("should deploy scenario with custom logging configuration") {
    val f = createKafkaFixture()

    def withManager(manager: K8sDeploymentManager)(action: ProcessVersion => Unit): Unit = {
      val version = ProcessVersion(VersionId(11), f.scenario.name, ProcessId(1234), List.empty, "testUser", Some(22))
      manager
        .processCommand(
          DMRunDeploymentCommand(
            version,
            DeploymentData.empty,
            f.scenario,
            DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
            )
          )
        )
        .futureValue

      action(version)
      cancelAndAssertCleanup(manager, version)
    }

    val customLogger = "test.passing.logback.conf"
    val logbackFile = {
      val tempFile = Files.createTempFile("test-logback", ".xml")
      Files.write(
        tempFile,
        s"""
           |<configuration scan="true" scanPeriod="5 seconds">
           |    <logger name="$customLogger" level="WARN"/>
           |</configuration>
           |""".stripMargin.getBytes
      )
      tempFile.toFile
    }
    val manager: K8sDeploymentManager = prepareManager(deployConfig =
      kafkaDeployConfig
        .withValue(
          "logbackConfigPath",
          fromAnyRef(logbackFile.toString)
        )
    )

    withManager(manager) { version =>
      eventually {
        val cm = k8s
          .listSelected[ListResource[ConfigMap]](requirementForName(version.processName))
          .futureValue
          .items
          .find {
            _.data.isDefinedAt("logback.xml")
          }
          .head
        cm.data("logback.xml").contains(customLogger) shouldBe true
      }
    }

    withManager(prepareManager()) { version =>
      eventually {
        val cm = k8s
          .listSelected[ListResource[ConfigMap]](requirementForName(version.processName))
          .futureValue
          .items
          .find {
            _.data.isDefinedAt("logback.xml")
          }
          .head
        cm.data("logback.xml").contains(customLogger) shouldBe false
      }
    }
  }

  test("should deploy scenarios with common logging conf") {
    val configMapName = "test" + new Random().nextInt(1000000)
    val f = createKafkaFixture(deployConfig =
      kafkaDeployConfig.withValue("commonConfigMapForLogback", fromAnyRef(configMapName))
    )

    f.withRunningScenario {
      // check if cm exists
      k8s.list[ListResource[ConfigMap]]().futureValue.items.exists(_.name == configMapName) shouldBe true

      // check if cm is actually mounted
      val pod = k8s.listSelected[ListResource[Pod]](requirementForName(f.version.processName)).futureValue.items.head
      pod.spec.get.volumes.exists(_.source match {
        case Volume.ConfigMapVolumeSource(`configMapName`, _, _, _) => true
        case _                                                      => false
      }) shouldBe true
    }
    // check that after cancelling scenario CM is still there
    k8s.list[ListResource[ConfigMap]]().futureValue.items.exists(_.name == configMapName) shouldBe true

    // cleanup
    k8s.delete[ConfigMap](configMapName)
  }

  test("should deploy within specified resource quota") {
    val f = createKafkaFixture()
    k8s.create(
      Quota(
        metadata = ObjectMeta(name = "nu-pods-limit"),
        spec = Some(Quota.Spec(hard = Map[String, Quantity]("pods" -> Quantity("3"))))
      )
    ) // two pods takes test setup
    f.withRunningScenario(())
    k8s.delete[Resource.Quota]("nu-pods-limit").futureValue
  }

  test("should not deploy when resource quota exceeded") {
    val f = createKafkaFixture()
    k8s.create(
      Quota(
        metadata = ObjectMeta(name = "nu-pods-limit"),
        spec = Some(Quota.Spec(hard = Map[String, Quantity]("pods" -> Quantity("2"))))
      )
    ) // two pods takes test setup

    f.manager
      .processCommand(
        DMValidateScenarioCommand(
          f.version,
          DeploymentData.empty,
          f.scenario,
          DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
            StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
          )
        )
      )
      .failed
      .futureValue shouldEqual
      ResourceQuotaExceededException("Cluster is full. Release some cluster resources.")

    cancelAndAssertCleanup(f.manager, f.version)
    k8s.delete[Resource.Quota]("nu-pods-limit").futureValue
  }

  test("should expose prometheus metrics") {
    val port = 8041
    val runtimeContainerConfig = baseRuntimeContainerConfig
      .withValue(
        "env",
        fromIterable(
          List(
            fromMap(
              Map(
                "name"  -> "PROMETHEUS_METRICS_PORT",
                "value" -> s"$port"
              ).asJava
            )
          ).asJava
        )
      )
      .withValue(
        "ports",
        fromIterable(
          List(
            fromMap(
              Map(
                "name"          -> "prometheus",
                "containerPort" -> port,
                "protocol"      -> "TCP"
              ).asJava
            )
          ).asJava
        )
      )
      .root()

    val f = createKafkaFixture(deployConfig =
      kafkaDeployConfig
        .withValue(
          "k8sDeploymentConfig.spec.template.spec.containers",
          fromIterable(List(runtimeContainerConfig).asJava)
        )
    )

    f.withRunningScenario {
      val pod = k8s.listSelected[ListResource[Pod]](requirementForName(f.version.processName)).futureValue.items.head
      pod.spec.get.containers.head.ports should contain theSameElementsAs List(Port(port, name = "prometheus"))

      k8sTestUtils.withPortForwarded(pod, port) { localPort =>
        eventually {
          basicRequest
            .get(uri"http://localhost:$localPort")
            .send(backend)
            .futureValue
            .body
            .toOption
            .get
            .contains("jvm_memory_bytes_committed") shouldBe true
        }
      }
    }
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    kafka.start()
  }

  override protected def cleanup(): Unit = {
    super.cleanup()
    kafka.stop()
  }

  private def cancelAndAssertCleanup(manager: K8sDeploymentManager, version: ProcessVersion) = {
    manager.processCommand(DMCancelScenarioCommand(version.processName, DeploymentData.systemUser)).futureValue
    eventually {
      manager.getScenarioDeploymentsStatuses(version.processName).map(_.value).futureValue shouldBe List.empty
    }
    assertNoGarbageLeft()
  }

  private lazy val kafkaDeployConfig: Config = baseDeployConfig("streaming")
    .withValue(
      KafkaConfigProperties.bootstrapServersProperty("configExecutionOverrides.modelConfig.kafka"),
      fromAnyRef(s"${KafkaK8sSupport.kafkaServiceName}:9092")
    )
    .withValue(
      KafkaConfigProperties.property("configExecutionOverrides.modelConfig.kafka", "schema.registry.url"),
      fromAnyRef(s"http://${KafkaK8sSupport.srServiceName}:8081")
    )

  private val modelData: LocalModelData = LocalModelData(
    ConfigFactory.empty
      // e.g. when we want to run Designer locally with some proxy?
      .withValue(KafkaConfigProperties.bootstrapServersProperty(), fromAnyRef("localhost:19092"))
      .withValue(KafkaConfigProperties.property("auto.offset.reset"), fromAnyRef("earliest"))
      .withValue("exceptionHandlingConfig.topic", fromAnyRef("errors")),
    List.empty
  )

  lazy val defaultSchema = """{"type":"object","properties":{"message":{"type":"string"}}}"""

  private def createKafkaFixture(
      modelData: LocalModelData = modelData,
      deployConfig: Config = kafkaDeployConfig,
      schema: String = defaultSchema
  ) = {
    val seed   = new Random().nextInt()
    val input  = s"ping-$seed"
    val output = s"pong-$seed"

    kafka.createSchema(s"$input-value", schema)
    kafka.createSchema(s"$output-value", schema)

    val manager = prepareManager(modelData, deployConfig)
    val scenario = ScenarioBuilder
      .streamingLite("foo scenario \u2620")
      .source("source", "kafka", "Topic" -> s"'$input'".spel, "Schema version" -> "'latest'".spel)
      .emptySink(
        "sink",
        "kafka",
        "Topic"                 -> s"'$output'".spel,
        "Schema version"        -> "'latest'".spel,
        "Key"                   -> "".spel,
        "Raw editor"            -> "true".spel,
        "Value validation mode" -> "'strict'".spel,
        "Value"                 -> "#input".spel
      )
    logger.info(s"Running kafka test on ${scenario.name} $input - $output")
    val version = ProcessVersion(VersionId(11), scenario.name, ProcessId(1234), List.empty, "testUser", Some(22))
    new KafkaTestFixture(
      inputTopic = input,
      outputTopic = output,
      manager = manager,
      scenario = scenario,
      version = version
    )
  }

  private def prepareManager(
      modelData: LocalModelData = modelData,
      deployConfig: Config = kafkaDeployConfig
  ): K8sDeploymentManager = {
    super.prepareManager(modelData, deployConfig)
  }

  private class KafkaTestFixture(
      val inputTopic: String,
      val outputTopic: String,
      manager: K8sDeploymentManager,
      scenario: CanonicalProcess,
      version: ProcessVersion
  ) extends K8sDeploymentManagerTestFixture(manager, scenario, version) {

    override def withRunningScenario(action: => Unit): Unit = {
      kafka.createTopic(inputTopic)
      kafka.createTopic(outputTopic)
      super.withRunningScenario(action)
      // should not fail
      assertNoGarbageLeft()
    }

  }

}
