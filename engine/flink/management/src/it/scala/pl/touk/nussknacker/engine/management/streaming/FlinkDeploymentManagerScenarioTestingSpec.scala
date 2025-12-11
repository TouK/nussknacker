package pl.touk.nussknacker.engine.management.streaming

import cats.effect.unsafe.implicits.global
import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.circe.Json
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.api.{ContextId, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MissingSinkFactory
import pl.touk.nussknacker.engine.api.context.ScenarioCompilationErrors
import pl.touk.nussknacker.engine.api.deployment.DMTestScenarioCommand
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestSourceSpecificFormatJsonRecord}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.management.WithProcessingTypeConfig
import pl.touk.nussknacker.test.{KafkaConfigProperties, VeryPatientScalaFutures}

import java.util.UUID
import scala.concurrent.Await
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

class FlinkDeploymentManagerScenarioTestingSpec
    extends AnyFlatSpec
    with Matchers
    with VeryPatientScalaFutures
    with WithProcessingTypeConfig
    with BeforeAndAfterAll {

  private val classPath: List[String] = TestModelClassPaths.scalaClasspath

  override def resolveProcessingTypeConfig(config: Config): Config = {
    super
      .resolveProcessingTypeConfig(config)
      .withValue("deploymentConfig.restUrl", fromAnyRef(s"http://dummy:1234"))
      .withValue(
        KafkaConfigProperties.bootstrapServersProperty("modelConfig.kafka"),
        ConfigValueFactory.fromAnyRef("kafka:1234")
      )
      .withValue("modelConfig.classPath", ConfigValueFactory.fromIterable(classPath.asJava))
      .withValue("category", fromAnyRef("Category1"))
  }

  private val scenarioTestData = ScenarioTestData(
    List(ScenarioTestSourceSpecificFormatJsonRecord(NodeId("startProcess"), Json.fromString("terefere")))
  )

  private lazy val (deploymentManager, releaseDeploymentMangerResources) =
    FlinkDeploymentManagerProviderHelper
      .createDeploymentManager(ConfigWithUnresolvedVersion(rawProcessingTypeConfig))
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
        NodeId("startProcess") -> List(
          (
            ContextId(scenarioName = processName, originatingNodeId = NodeId("startProcess"), taskId = 0, index = 0),
            Map("input" -> variable("terefere"))
          )
        ),
        NodeId("nightFilter") -> List(
          (
            ContextId(scenarioName = processName, originatingNodeId = NodeId("startProcess"), taskId = 0, index = 0),
            Map("input" -> variable("terefere"))
          )
        ),
        NodeId("endSend") -> List(
          (
            ContextId(scenarioName = processName, originatingNodeId = NodeId("startProcess"), taskId = 0, index = 0),
            Map("input" -> variable("terefere"))
          )
        )
      )
    }
  }

  // this checks that we are setting watermark strategy in test if source watermarkStrategy method returns None
  it should "set timestamps even if no watermark strategy is provided by source" in {
    val processName    = ProcessName(UUID.randomUUID().toString)
    val processVersion = ProcessVersion.empty.copy(processName = processName)

    val process = SampleProcess.prepareProcessWithNoWatermarkStrategyForTest(processName)

    whenReady(deploymentManager.processCommand(DMTestScenarioCommand(processVersion, process, scenarioTestData))) { r =>
      val variablesInNodes = r.nodeResults.map { case (key, values) => (key, values.map(v => (v.id, v.variables))) }
      val timestampAsJson =
        variablesInNodes(NodeId("endSend"))(0)._2("eventTimeTimestampCopy")
      val timestampReadAsObject = timestampAsJson.asObject
      val firstValue            = timestampReadAsObject.get.values.toList(0)
      val timestamp             = firstValue.asNumber.get.toLong.get
      timestamp shouldBe System.currentTimeMillis() +- 1000 * 60
    }
  }

  it should "return correct error messages" in {
    val processName    = ProcessName(UUID.randomUUID().toString)
    val processVersion = ProcessVersion.empty.copy(processName = processName)

    val process = ScenarioBuilder
      .streaming(processName.value)
      .source("startProcess", "kafka-transaction")
      .emptySink("endSend", "sendSmsNotExist")

    the[ScenarioCompilationErrors] thrownBy {
      Await.result(
        deploymentManager.processCommand(DMTestScenarioCommand(processVersion, process, scenarioTestData)),
        patienceConfig.timeout
      )
    } should matchPattern {
      case ScenarioCompilationErrors(MissingSinkFactory("sendSmsNotExist", NodeId("endSend")) :: Nil) =>
    }
  }

  private def variable(value: String): Json =
    Json.obj("pretty" -> Json.fromString(value))

}
