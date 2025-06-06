package pl.touk.nussknacker.engine.management.streaming

import cats.effect.unsafe.implicits.global
import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.circe.Json
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.api.{ContextId, ProcessVersion}
import pl.touk.nussknacker.engine.api.deployment.DMTestScenarioCommand
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.testmode.TestProcess.ResultContext
import pl.touk.nussknacker.test.{KafkaConfigProperties, VeryPatientScalaFutures, WithConfig}

import java.util.UUID
import scala.concurrent.Await
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

class FlinkDeploymentManagerScenarioTestingSpec
    extends AnyFlatSpec
    with Matchers
    with VeryPatientScalaFutures
    with WithConfig
    with BeforeAndAfterAll {

  private val classPath: List[String] = TestModelClassPaths.scalaClasspath

  override protected val configFilename: Option[String] = Some("application.conf")

  override def resolveConfig(config: Config): Config = {
    super
      .resolveConfig(config)
      .withValue("deploymentConfig.restUrl", fromAnyRef(s"http://dummy:1234"))
      .withValue(
        KafkaConfigProperties.bootstrapServersProperty("modelConfig.kafka"),
        ConfigValueFactory.fromAnyRef("kafka:1234")
      )
      .withValue("modelConfig.classPath", ConfigValueFactory.fromIterable(classPath.asJava))
      .withValue("category", fromAnyRef("Category1"))
  }

  private val scenarioTestData = ScenarioTestData(
    List(ScenarioTestJsonRecord("startProcess", Json.fromString("terefere")))
  )

  private lazy val (deploymentManager, releaseDeploymentMangerResources) =
    FlinkDeploymentManagerProviderHelper
      .createDeploymentManager(ConfigWithUnresolvedVersion(config))
      .allocated
      .unsafeRunSync()

  override protected def afterAll(): Unit = {
    releaseDeploymentMangerResources.unsafeRunSync()
    super.afterAll()
  }

  it should "run scenario in test mode" in {
    val processName    = ProcessName(UUID.randomUUID().toString)
    val processVersion = ProcessVersion.empty.copy(processName = processName)

    val process = SampleProcess.prepareProcess(processName)

    whenReady(deploymentManager.processCommand(DMTestScenarioCommand(processVersion, process, scenarioTestData))) { r =>
      r.nodeResults.map(r => (r._1, r._2.map(r => (r.id, r.variables)))) shouldBe Map(
        "startProcess" -> List(
          (
            ContextId(processName.value, "startProcess", 0, 0),
            Map("input" -> variable("terefere"))
          )
        ),
        "nightFilter" -> List(
          (
            ContextId(processName.value, "startProcess", 0, 0),
            Map("input" -> variable("terefere"))
          )
        ),
        "endSend" -> List(
          (
            ContextId(processName.value, "startProcess", 0, 0),
            Map("input" -> variable("terefere"))
          )
        )
      )
    }
  }

  it should "return correct error messages" in {
    val processName    = ProcessName(UUID.randomUUID().toString)
    val processVersion = ProcessVersion.empty.copy(processName = processName)

    val process = ScenarioBuilder
      .streaming(processName.value)
      .source("startProcess", "kafka-transaction")
      .emptySink("endSend", "sendSmsNotExist")

    val caught = intercept[IllegalArgumentException] {
      Await.result(
        deploymentManager.processCommand(DMTestScenarioCommand(processVersion, process, scenarioTestData)),
        patienceConfig.timeout
      )
    }
    caught.getMessage shouldBe "Compilation errors: MissingSinkFactory(sendSmsNotExist,endSend)"
  }

  private def variable(value: String): Json =
    Json.obj("pretty" -> Json.fromString(value))

}
