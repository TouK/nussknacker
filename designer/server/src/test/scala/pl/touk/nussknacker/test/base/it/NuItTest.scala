package pl.touk.nussknacker.test.base.it

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.test.DefaultUniquePortProvider
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.config.WithDesignerConfig
import pl.touk.nussknacker.ui.LoadableConfigBasedNussknackerConfig
import pl.touk.nussknacker.ui.config.DesignerConfigLoader
import pl.touk.nussknacker.ui.factory.NussknackerAppFactory
import pl.touk.nussknacker.ui.process.processingtype.loader._

trait NuItTest extends WithHsqlDbTesting with DefaultUniquePortProvider with WithClock with BeforeAndAfterAll {
  this: Suite with WithDesignerConfig =>

  private val port = nextPort()

  val nuDesignerHttpAddress = s"http://localhost:$port"

  private var releaseAppResources: IO[Unit] = IO.unit

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val nussknackerConfig = new LoadableConfigBasedNussknackerConfig(
      IO.delay(DesignerConfigLoader.from(adjustNuTestConfig()))
    )
    releaseAppResources = new NussknackerAppFactory(nussknackerConfig)
      .createApp(clock = clock)
      .allocated
      .unsafeRunSync()
      ._2
  }

  override protected def afterAll(): Unit = {
    releaseAppResources.unsafeRunSync()
    super.afterAll()
  }

  private def adjustNuTestConfig(): Config = {
    designerConfig
      .withValue("db", testDbConfig.getConfig("db").root())
      .withValue("http.port", fromAnyRef(port))
      .withValue("managersDir", fromAnyRef("designer/server/work/managers"))
  }

}
