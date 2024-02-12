package pl.touk.nussknacker.tests.base.it

import cats.effect.unsafe.implicits.global
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.test.DefaultUniquePortProvider
import pl.touk.nussknacker.tests.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.tests.config.WithDesignerConfig
import pl.touk.nussknacker.ui.factory.NussknackerAppFactory

trait NuItTest2 extends WithHsqlDbTesting with DefaultUniquePortProvider with BeforeAndAfterAll {
  this: Suite with WithDesignerConfig =>

  private val port = nextPort()

  val nuDesignerHttpAddress = s"http://localhost:$port"

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
    designerConfig
      .withValue("db", testDbConfig.getConfig("db").root())
      .withValue("http.port", fromAnyRef(port))
  }

}
