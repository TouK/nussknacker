package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.EspItTest
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withoutPermissions
import pl.touk.nussknacker.ui.config.AnalyticsConfig
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthenticationConfiguration

import java.net.URLDecoder
import java.nio.charset.StandardCharsets


class SettingsResourcesSpec extends AnyFunSpec with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  private val authenticationConfig: BasicAuthenticationConfiguration = BasicAuthenticationConfiguration.create(testConfig)
  private val analyticsConfig: Option[AnalyticsConfig] = AnalyticsConfig(testConfig)

  private val settingsRoute = new SettingsResources(featureTogglesConfig, authenticationConfig.name, analyticsConfig)

  //Values are exists at test/resources/application.conf
  private val intervalTimeProcesses = 20000
  private val intervalTimeHealthCheck = 30000

  private val ReportsUrlPattern = "https://stats\\.nussknacker\\.io/\\?fingerprint=(.*)&version=(.*)".r

  it("should return base intervalSettings") {
    getSettings ~> check {
      status shouldBe StatusCodes.OK
      val responseSettings = responseAs[UISettings]
      val data = responseSettings.features

      data.intervalTimeSettings.processes shouldBe intervalTimeProcesses
      data.intervalTimeSettings.healthCheck shouldBe intervalTimeHealthCheck
    }
  }

  it("should return usage reports settings with default generated url") {
    getSettings ~> check {
      status shouldBe StatusCodes.OK
      val responseSettings = responseAs[UISettings]
      responseSettings.features.usageStatisticsReports.enabled shouldBe true

      noException should be thrownBy {
        responseSettings.features.usageStatisticsReports.url match {
          case ReportsUrlPattern(fingerprint, version) if fingerprint.startsWith("gen-") && URLDecoder.decode(version, StandardCharsets.UTF_8) == BuildInfo.version => ()
        }
      }
    }
  }

  private def getSettings: RouteTestResult = Get(s"/settings") ~> withoutPermissions(settingsRoute)
}
