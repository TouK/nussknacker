package pl.touk.nussknacker.engine.baseengine.kafka

import com.dimafeng.testcontainers.{Container, ForAllTestContainer, GenericContainer}
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import org.testcontainers.containers.BindMode
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.nio.file.Path

class NuKafkaEngineDockerTest extends FunSuite with ForAllTestContainer with KafkaSpec  with VeryPatientScalaFutures with Matchers with BeforeAndAfter {

  private val nuEngineRuntimeDockerName = "touk/nussknacker-standalone-engine:1.0.1-SNAPSHOT"
  private val dockerPort = 8080

  private val processId = "runTransactionSimpleScenarioViaDocker"
  private val inputTopic = s"input-$processId"
  private val outputTopic = s"output-$processId"

  override val container: Container =
    GenericContainer(
      nuEngineRuntimeDockerName,
      exposedPorts = Seq(dockerPort),
      classpathResourceMapping = Seq((
        //fixme: now scenario is hardcoded in target classes because it didn't read from scenarioPath
        "scenario.json",
        "/opt/nussknacker/conf/scenario.json",
        BindMode.READ_ONLY))
    )

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    kafkaClient.createTopic(inputTopic)
    kafkaClient.createTopic(outputTopic, 1)

  }

  test("simple test run") {
    true shouldBe true
  }

  private def scenarioPath: Path = {
    val targetItClassesDir = Path.of(getClass.getResource("/").toURI)
    println(targetItClassesDir)
    val baseModuleDir = targetItClassesDir.getParent.getParent.getParent
    val resourcesDir = baseModuleDir.resolve("src/it/resources")
    resourcesDir.resolve("scenario.json")
  }

}