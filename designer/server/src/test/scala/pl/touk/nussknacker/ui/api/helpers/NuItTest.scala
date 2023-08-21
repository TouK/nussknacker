package pl.touk.nussknacker.ui.api.helpers

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.test.DefaultUniquePortProvider
import pl.touk.nussknacker.ui.factory.NussknackerAppFactory
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion.TestsConfig

trait NuItTest
  extends WithHsqlDbTesting
    with DefaultUniquePortProvider
    with TestPermissions
    with BeforeAndAfterAll {
  self: Suite =>

  private val port = nextPort()

  val nuDesignerHttpAddress = s"http://localhost:$port"

  private val (_, releaseAppResources) = {
    new NussknackerAppFactory()
      .createApp(TestsConfig.withValue("http.port", fromAnyRef(port)))
      .allocated
      .unsafeRunSync()
  }

  abstract override def afterAll(): Unit = {
    releaseAppResources.unsafeRunSync()
    super.afterAll()
  }

}