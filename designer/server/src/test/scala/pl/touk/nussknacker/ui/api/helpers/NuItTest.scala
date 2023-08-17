package pl.touk.nussknacker.ui.api.helpers

import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.ui.factory.NussknackerApp
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

trait NuItTest
  extends WithHsqlDbTesting
    with TestPermissions
    with BeforeAndAfterAll {
  self: Suite =>

  val nuDesignerHttpAddress = "http://localhost:8080"

  private val (_, releaseAppResources) = {
    new NussknackerApp(ConfigWithScalaVersion.TestsConfig)
      .init()
      .allocated
      .unsafeRunSync()
  }

  abstract override def afterAll(): Unit = {
    releaseAppResources.unsafeRunSync()
    super.afterAll()
  }

}