package pl.touk.nussknacker.tests.base.it

import cats.effect.unsafe.implicits.global
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.test.DefaultUniquePortProvider
import pl.touk.nussknacker.tests.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.ui.factory.NussknackerAppFactory

trait NuItTest2 extends WithHsqlDbTesting with DefaultUniquePortProvider with BeforeAndAfterAll {
  this: Suite =>

  private val port = nextPort()

  val nuDesignerHttpAddress = s"http://localhost:$port"

  def nuTestConfig: Config

  private val (_, releaseAppResources) = {
    new NussknackerAppFactory()
      .createApp(adjustNuTestConfig())
      .allocated
      .unsafeRunSync()
  }

  override def afterAll(): Unit = {
    releaseAppResources.unsafeRunSync()
    super.afterAll()
  }

  private def adjustNuTestConfig(): Config = {
    nuTestConfig
      .withValue("db", testDbConfig.getConfig("db").root())
      .withValue("http.port", fromAnyRef(port))
  }

}

// todo: for every scenario type
trait WithMockableDeploymentManager extends NuItTest2 {
  this: Suite =>

  // todo: for every scenario type
  abstract override def nuTestConfig: Config = super.nuTestConfig
    .withValue(
      "scenarioTypes.streaming.deploymentConfig",
      ConfigFactory
        .parseString("""{ type: "mockable" }""")
        .root()
    )

}
