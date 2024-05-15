package pl.touk.nussknacker.ui.statistics

import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.mockito.MockitoSugar
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.db.timeseries.FEStatisticsRepository

import scala.collection.immutable.ListMap
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

  test("should generate correct url with encoded params") {
    UsageStatisticsReportsSettingsService.prepareUrlString(
      ListMap("f" -> "a b", "v" -> "1.6.5-a&b=c")
    ) shouldBe "https://stats.nussknacker.io/?f=a+b&v=1.6.5-a%26b%3Dc"
  }

  test("should not generate an url if it's not configured") {
    val sut = new UsageStatisticsReportsSettingsService(
      config = UsageStatisticsReportsConfig(enabled = false, None, None),
      fingerprintService = fingerprintService,
      fetchNonArchivedScenariosInputData = () => Future.successful(Right(Nil)),
      fetchActivity = (_: List[ScenarioStatisticsInputData]) => Future.successful(Right(Nil)),
      fetchComponentList = () => Future.successful(Right(Nil)),
      fetchFeStatistics = () => Future.successful(Map.empty[String, Long])
    )

    sut.prepareStatisticsUrl().futureValue shouldBe Right(None)
  }

  test("should return error if the URL cannot be constructed") {
    UsageStatisticsReportsSettingsService.toURL("xd://stats.nussknacker.io/?f=a+b") shouldBe
      Left(CannotGenerateStatisticsError)
  }

}
