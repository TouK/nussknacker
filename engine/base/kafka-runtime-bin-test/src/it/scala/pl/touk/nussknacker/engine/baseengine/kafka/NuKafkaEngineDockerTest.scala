package pl.touk.nussknacker.engine.baseengine.kafka

import com.dimafeng.testcontainers.{FixedHostPortGenericContainer, ForAllTestContainer}
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import org.testcontainers.containers.BindMode
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class NuKafkaEngineDockerTest extends FunSuite with ForAllTestContainer with KafkaSpec  with VeryPatientScalaFutures with Matchers with BeforeAndAfter {

  private val nuEngineRuntimeDockerName = "touk/nussknacker-lite-kafka-runtime:1.0.1-SNAPSHOT"
  private val dockerPort = 8080

  private val processId = "runSimpleScenarioViaDocker"
  private val inputTopic = s"input-$processId"
  private val outputTopic = s"output-$processId"

  override val container: FixedHostPortGenericContainer =
    FixedHostPortGenericContainer(
      nuEngineRuntimeDockerName,
      classpathResourceMapping = Seq((
        "scenario.json",
        "/opt/nussknacker/conf/scenario.json",
        BindMode.READ_ONLY)),
      exposedHostPort = 80,
      exposedContainerPort = dockerPort,
    )


  test("container should start") {

  }

}