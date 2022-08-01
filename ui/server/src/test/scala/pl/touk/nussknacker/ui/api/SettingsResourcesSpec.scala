package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.EspItTest
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withoutPermissions
import pl.touk.nussknacker.ui.config.AnalyticsConfig
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthenticationConfiguration

class SettingsResourcesSpec extends FunSpec with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  private val authenticationConfig: BasicAuthenticationConfiguration = BasicAuthenticationConfiguration.create(testConfig)
  private val analyticsConfig: Option[AnalyticsConfig] = AnalyticsConfig(testConfig)

  private val settingsRoute = new SettingsResources(featureTogglesConfig, authenticationConfig.name, analyticsConfig)

  //Values are exists at test/resources/application.conf
  private val intervalTimeProcesses = 20000
  private val intervalTimeHealthCheck = 30000

  it("should return base intervalSettings") {
    getSettings ~> check {
      status shouldBe StatusCodes.OK
      val responseSettings = responseAs[UISettings]
      val data = responseSettings.features

      data.intervalTimeSettings.processes shouldBe intervalTimeProcesses
      data.intervalTimeSettings.healthCheck shouldBe intervalTimeHealthCheck
    }
  }

  private def getSettings: RouteTestResult = Get(s"/settings") ~> withoutPermissions(settingsRoute)
}
