package pl.touk.nussknacker.engine.baseengine.kafka

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import org.scalatest.{FunSuite, Matchers}
import org.testcontainers.containers.BindMode
import pl.touk.nussknacker.engine.version.BuildInfo

class NuKafkaEngineDockerTest extends FunSuite with ForAllTestContainer with Matchers {


  private val dockerTag = sys.env.getOrElse("dockerTagName", BuildInfo.version)
  private val nuEngineRuntimeDockerName = s"touk/nussknacker-lite-kafka-runtime:${dockerTag}"

  override val container: GenericContainer = {
    GenericContainer(
      nuEngineRuntimeDockerName,
      classpathResourceMapping = Seq((
        "LiteKafkaTransactionalScenario.json",
        "/opt/nussknacker/conf/LiteKafkaTransactionalScenario.json",
        BindMode.READ_ONLY)),
    )
  }


  test("container should start") {

  }

}