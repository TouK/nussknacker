package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.tests.TestFactory.withoutPermissions
import pl.touk.nussknacker.tests.base.it.NuResourcesTest
import pl.touk.nussknacker.ui.config.AnalyticsConfig
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthenticationConfiguration
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettings

class SettingsResourcesSpec
    extends AnyFunSpec
    with ScalatestRouteTest
    with FailFastCirceSupport
    with Matchers
    with PatientScalaFutures
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with NuResourcesTest {

  private val authenticationConfig: BasicAuthenticationConfiguration =
    BasicAuthenticationConfiguration.create(testConfig)
  private val analyticsConfig: Option[AnalyticsConfig] = AnalyticsConfig(testConfig)

  private val settingsRoute = new SettingsResources(
    featureTogglesConfig,
    authenticationConfig.name,
    analyticsConfig,
    UsageStatisticsReportsSettings(enabled = false, "http://just.test")
  )

  // Values are exists at test/resources/application.conf
  private val intervalTimeProcesses   = 20000
  private val intervalTimeHealthCheck = 30000

  it("should return base intervalSettings") {
    getSettings ~> check {
      status shouldBe StatusCodes.OK
      val responseSettings = responseAs[UISettings]
      val data             = responseSettings.features

      data.intervalTimeSettings.processes shouldBe intervalTimeProcesses
      data.intervalTimeSettings.healthCheck shouldBe intervalTimeHealthCheck
    }
  }

  private def getSettings: RouteTestResult = Get(s"/settings") ~> withoutPermissions(settingsRoute)
}
