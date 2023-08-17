package pl.touk.nussknacker.ui.api.helpers

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import pl.touk.nussknacker.ui.factory.NussknackerApp
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

trait NuItTest
  extends WithHsqlDbTesting
    with TestPermissions
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with LazyLogging {
  self: Suite with Matchers with ScalaFutures =>

  private val (_, releaseAppResources) = {
    new NussknackerApp(ConfigWithScalaVersion.TestsConfig)
      .init()
      .allocated
      .unsafeRunSync()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    releaseAppResources.unsafeRunSync()
  }

}