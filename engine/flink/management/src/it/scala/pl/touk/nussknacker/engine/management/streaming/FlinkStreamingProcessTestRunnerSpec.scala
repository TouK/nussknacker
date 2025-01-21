package pl.touk.nussknacker.engine.management.streaming

import cats.effect.unsafe.implicits.global
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigValueFactory}
import io.circe.Json
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DMTestScenarioCommand
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.testmode.TestProcess.ResultContext
import pl.touk.nussknacker.test.{KafkaConfigProperties, VeryPatientScalaFutures, WithConfig}

import java.util.UUID
import scala.concurrent.Await
import scala.jdk.CollectionConverters._

class FlinkStreamingProcessTestRunnerSpec
    extends AnyFlatSpec
    with Matchers
    with VeryPatientScalaFutures
    with WithConfig
    with BeforeAndAfterAll {

  private val classPath: List[String] = ClassPaths.scalaClasspath

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
    FlinkStreamingDeploymentManagerProviderHelper
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
      r.nodeResults shouldBe Map(
        "startProcess" -> List(ResultContext(s"$processName-startProcess-0-0", Map("input" -> variable("terefere")))),
        "nightFilter"  -> List(ResultContext(s"$processName-startProcess-0-0", Map("input" -> variable("terefere")))),
        "endSend"      -> List(ResultContext(s"$processName-startProcess-0-0", Map("input" -> variable("terefere"))))
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
