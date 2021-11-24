package pl.touk.nussknacker.engine.baseengine.kafka

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import org.testcontainers.containers.BindMode
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class NuKafkaEngineDockerTest extends FunSuite with ForAllTestContainer with KafkaSpec  with VeryPatientScalaFutures with Matchers with BeforeAndAfter {

  private val nuEngineRuntimeDockerName = "touk/nussknacker-lite-kafka-runtime:latest"

  override val container: GenericContainer = {
    GenericContainer(
      nuEngineRuntimeDockerName,
      classpathResourceMapping = Seq((
        "scenario.json",
        "/opt/nussknacker/conf/scenario.json",
        BindMode.READ_ONLY)),
    )
  }


  test("container should start") {

  }

}