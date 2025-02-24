package pl.touk.nussknacker.ui.api

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar.mock
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.utils.domain.TestFactory.withoutPermissions
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthenticationConfiguration
import pl.touk.nussknacker.ui.statistics.{Fingerprint, FingerprintService, StatisticError}

import scala.concurrent.Future

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
  private val usageStatisticsReportsConfig: UsageStatisticsReportsConfig =
    UsageStatisticsReportsConfig(true, true, None, None)
  private val sampleFingerprint = "sample fingerprint"

  private val mockedFingerprintService: FingerprintService = mock[FingerprintService](
    new Answer[Future[Either[StatisticError, Fingerprint]]] {
      override def answer(invocation: InvocationOnMock): Future[Either[StatisticError, Fingerprint]] =
        Future.successful(Right(new Fingerprint(sampleFingerprint)))
    }
  )

  private val settingsRoute = new SettingsResources(
    featureTogglesConfig,
    authenticationConfig.name,
    usageStatisticsReportsConfig,
    mockedFingerprintService
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
      data.usageStatisticsReports.enabled shouldBe true
      data.usageStatisticsReports.errorReportsEnabled shouldBe true
      data.usageStatisticsReports.fingerprint shouldBe Some(sampleFingerprint)
    }
  }

  private def getSettings: RouteTestResult = Get(s"/settings") ~> withoutPermissions(settingsRoute)
}
