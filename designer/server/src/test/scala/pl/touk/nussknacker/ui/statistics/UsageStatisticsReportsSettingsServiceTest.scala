package pl.touk.nussknacker.ui.statistics

import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.mockito.MockitoSugar
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UsageStatisticsReportsSettingsServiceTest
    extends AnyFunSuite
    with Matchers
    with EitherValues
    with PatientScalaFutures
    with TableDrivenPropertyChecks
    with MockitoSugar {
  private val fingerprintService: FingerprintService = mock[FingerprintService]

  test("should not generate an url if it's not configured") {
    val sut = new UsageStatisticsReportsSettingsService(
      config = UsageStatisticsReportsConfig(enabled = false, None, None),
      fingerprintService = fingerprintService,
      fetchNonArchivedScenariosInputData = () => Future.successful(Right(Nil)),
      fetchActivity = (_: List[ScenarioStatisticsInputData]) => Future.successful(Right(Nil)),
      fetchComponentList = () => Future.successful(Right(Nil)),
      fetchFeStatistics = () => Future.successful(Map.empty[String, Long])
    )

    sut.prepareStatisticsUrl().futureValue shouldBe Right(Nil)
  }

}
