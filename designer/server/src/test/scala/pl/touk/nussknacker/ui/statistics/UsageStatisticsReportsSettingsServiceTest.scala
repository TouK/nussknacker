package pl.touk.nussknacker.ui.statistics

import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.mockito.MockitoSugar
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig

import java.time.Clock
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

  private val sampleFingerprint = "fooFingerprint"

  private val mockedFingerprintService: FingerprintService = mock[FingerprintService](
    new Answer[Future[Either[StatisticError, Fingerprint]]] {
      override def answer(invocation: InvocationOnMock): Future[Either[StatisticError, Fingerprint]] =
        Future.successful(Right(new Fingerprint(sampleFingerprint)))
    }
  )

  test("should not generate an url if it's not configured") {
    val sut = new UsageStatisticsReportsSettingsService(
      config = UsageStatisticsReportsConfig(enabled = false, None, None),
      urlConfig = StatisticUrlConfig(),
      fingerprintService = fingerprintService,
      fetchNonArchivedScenariosInputData = () => Future.successful(Right(Nil)),
      fetchActivity = (_: List[ScenarioStatisticsInputData]) => Future.successful(Right(Nil)),
      fetchFeStatistics = () => Future.successful(Map.empty[String, Long]),
      components = List.empty,
      designerClock = Clock.systemUTC()
    )

    sut.prepareStatisticsUrl().futureValue shouldBe Right(Nil)
  }

  test("should include all statisticKeys even without any scenarios created") {
    val url = new UsageStatisticsReportsSettingsService(
      config = UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), Some("source")),
      urlConfig = StatisticUrlConfig(),
      fingerprintService = mockedFingerprintService,
      fetchNonArchivedScenariosInputData = () => Future.successful(Right(List.empty)),
      fetchActivity = _ => Future.successful(Right(List.empty)),
      fetchComponentList = () => Future.successful(Right(List.empty)),
      fetchFeStatistics = () => Future.successful(Map.empty[String, Long]),
      components = List.empty,
      designerClock = Clock.systemUTC()
    ).prepareStatisticsUrl().futureValue.value.reduce(_ ++ _)

    List(
      AuthorsCount,
      CategoriesCount,
      ComponentsCount,
      VersionsMedian,
      AttachmentsTotal,
      AttachmentsAverage,
      VersionsMax,
      VersionsMin,
      VersionsAverage,
      UptimeInSecondsAverage,
      UptimeInSecondsMax,
      UptimeInSecondsMin,
      CommentsAverage,
      CommentsTotal,
      FragmentsUsedMedian,
      FragmentsUsedAverage,
      NodesMedian,
      NodesAverage,
      NodesMax,
      NodesMin,
      ScenarioCount,
      FragmentCount,
      UnboundedStreamCount,
      BoundedStreamCount,
      RequestResponseCount,
      FlinkDMCount,
      LiteK8sDMCount,
      LiteEmbeddedDMCount,
      UnknownDMCount,
      ActiveScenarioCount,
      NuSource,
      NuFingerprint,
      NuVersion,
      CorrelationIdStat,
      DesignerUptimeInSeconds,
    )
      .map(_.name)
      .foreach(stat => url should include(stat))
  }

}
