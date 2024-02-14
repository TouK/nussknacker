package pl.touk.nussknacker.test.base.it

import cats.effect.unsafe.implicits.global
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.test.DefaultUniquePortProvider
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.config.ConfigWithScalaVersion
import pl.touk.nussknacker.test.utils.domain.TestPermissions
import pl.touk.nussknacker.ui.factory.NussknackerAppFactory

trait NuItTest extends WithHsqlDbTesting with DefaultUniquePortProvider with TestPermissions with BeforeAndAfterAll {
  this: Suite =>

  private val port = nextPort()

  val nuDesignerHttpAddress = s"http://localhost:$port"

  def nuTestConfig: Config = ConfigWithScalaVersion.TestsConfig

  private val (_, releaseAppResources) = {
    new NussknackerAppFactory()
      .createApp(adjustNuTestConfig())
      .allocated
      .unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    releaseAppResources.unsafeRunSync()
    super.afterAll()
  }

  private def adjustNuTestConfig() = {
    nuTestConfig
      .withValue("db", testDbConfig.getConfig("db").root())
      .withValue("http.port", fromAnyRef(port))
  }

}

trait WithMockableDeploymentManager extends NuItTest {
  this: Suite =>

  abstract override def nuTestConfig: Config = super.nuTestConfig
    .withValue(
      "scenarioTypes.streaming.deploymentConfig",
      ConfigFactory
        .parseString("""{ type: "mockable" }""")
        .root()
    )

}
