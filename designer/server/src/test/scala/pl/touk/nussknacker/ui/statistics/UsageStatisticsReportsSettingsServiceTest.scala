package pl.touk.nussknacker.ui.statistics

import custom.component.CustomComponentService
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.mockito.MockitoSugar
import pl.touk.nussknacker.engine.api.component.{DesignerWideComponentId, ProcessingMode}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.utils.{QueryParamsHelper, StatisticEncryptionSupport}
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.process.processingtype.DeploymentManagerType
import pl.touk.nussknacker.ui.statistics.ScenarioStatistics.{
  emptyActivityStatistics,
  emptyComponentStatistics,
  emptyGeneralStatistics,
  emptyScenarioStatistics,
  emptyUptimeStats
}

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

  private val clock     = Clock.systemDefaultZone()
  private val uuidRegex = "[0-9a-f]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8}"
  private val emptyScenarioRelatedStatistics = (emptyScenarioStatistics ++ emptyComponentStatistics ++
    emptyActivityStatistics ++ emptyUptimeStats ++ emptyGeneralStatistics).keySet

  private val allScenarioRelatedStatistics = Set(
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
  ).map(_.name)

  private val notScenarioRelatedStatistics = Set(
    NuSource,
    NuVersion,
    DesignerUptimeInSeconds,
    NuFingerprint,
    RequestIdStat,
  ).map(_.name)

  private val cfg = StatisticUrlConfig(publicEncryptionKey = StatisticEncryptionSupport.publicKey)

  test("should determine query params with version and source") {
    val urls = new UsageStatisticsReportsSettingsService(
      config = UsageStatisticsReportsConfig(enabled = true, errorReportsEnabled = true, Some(sampleFingerprint), None),
      fingerprintService = mockedFingerprintService,
      fetchNonArchivedScenariosInputData = () => Future.successful(Right(List.empty)),
      fetchActivity = () => Future.successful(Map.empty),
      fetchFeStatistics = () => Future.successful(RawFEStatistics.empty),
      components = List.empty,
      componentUsage = () => Future.successful(Map.empty),
      designerClock = clock
    ).prepareStatisticsUrl()
      .futureValue
      .value
      .prepareURLs(cfg)
      .value
      .map(url => StatisticEncryptionSupport.decodeToString(url.toString))

    urls.length shouldEqual 1
    val urlString = urls.head
    urlString should include(s"fingerprint=$sampleFingerprint")
    urlString should include regex s"$RequestIdStat=$uuidRegex"
    urlString should include("source=sources")
    urlString should include(s"version=${BuildInfo.version}")
  }

  test("should determine statistics for components") {
    val url = new UsageStatisticsReportsSettingsService(
      config = UsageStatisticsReportsConfig(enabled = true, errorReportsEnabled = true, Some(sampleFingerprint), None),
      fingerprintService = mockedFingerprintService,
      fetchNonArchivedScenariosInputData =
        () => Future.successful(Right(List(nonRunningScenario, k8sRRScenario, runningScenario))),
      fetchActivity = () => Future.successful(Map.empty),
      fetchFeStatistics = () => Future.successful(RawFEStatistics.empty),
      components = componentWithImplementation,
      componentUsage = () => Future.successful(componentUsagesMap),
      designerClock = clock
    ).prepareStatisticsUrl()
      .futureValue
      .value
      .prepareURLs(cfg)
      .value
      .map(url => StatisticEncryptionSupport.decodeToString(url.toString))
      .head

    url should include("c_srvcccntsrvc=5")
    url should include("c_cstm=2")
    url shouldNot include("c_bltnchc")
  }

  test("should combined statistics for all scenarios") {
    val url = new UsageStatisticsReportsSettingsService(
      UsageStatisticsReportsConfig(enabled = true, errorReportsEnabled = true, Some(sampleFingerprint), None),
      mockedFingerprintService,
      () => Future.successful(Right(List(nonRunningScenario, runningScenario, fragment, k8sRRScenario))),
      () => Future.successful(processActivityMap),
      () => Future.successful(RawFEStatistics.empty),
      componentWithImplementation,
      () => Future.successful(componentUsagesMap),
      clock
    ).prepareStatisticsUrl()
      .futureValue
      .value
      .prepareURLs(cfg)
      .value
      .map(url => StatisticEncryptionSupport.decodeToString(url.toString))
      .head
    val queryParams = QueryParamsHelper.extractFromURLString(url)

    val expectedStats = Map(
      AuthorsCount           -> 1,
      AttachmentsTotal       -> 8,
      AttachmentsAverage     -> 2,
      CategoriesCount        -> 1,
      CommentsTotal          -> 4,
      CommentsAverage        -> 1,
      VersionsMedian         -> 2,
      VersionsMax            -> 3,
      VersionsMin            -> 1,
      VersionsAverage        -> 2,
      UptimeInSecondsAverage -> 0,
      UptimeInSecondsMax     -> 0,
      UptimeInSecondsMin     -> 0,
      ComponentsCount        -> 4,
      FragmentsUsedMedian    -> 1,
      FragmentsUsedAverage   -> 1,
      NodesMedian            -> 3,
      NodesAverage           -> 2,
      NodesMax               -> 4,
      NodesMin               -> 2,
      ScenarioCount          -> 3,
      FragmentCount          -> 1,
      UnboundedStreamCount   -> 3,
      BoundedStreamCount     -> 0,
      RequestResponseCount   -> 1,
      FlinkDMCount           -> 3,
      LiteK8sDMCount         -> 1,
      LiteEmbeddedDMCount    -> 0,
      UnknownDMCount         -> 0,
      ActiveScenarioCount    -> 2,
      "c_srvcccntsrvc"       -> 5,
      "c_cstm"               -> 2,
    ).map { case (k, v) => (k.toString, v.toString) }

    queryParams should contain allElementsOf expectedStats
  }

  test("should provide all statistics even without any scenarios present") {
    val url = new UsageStatisticsReportsSettingsService(
      UsageStatisticsReportsConfig(enabled = true, errorReportsEnabled = true, Some(sampleFingerprint), None),
      mockedFingerprintService,
      () => Future.successful(Right(List.empty)),
      () => Future.successful(Map.empty),
      () => Future.successful(RawFEStatistics.empty),
      List.empty,
      () => Future.successful(Map.empty),
      clock
    ).prepareStatisticsUrl()
      .futureValue
      .value
      .prepareURLs(cfg)
      .value
      .map(url => StatisticEncryptionSupport.decodeToString(url.toString))
      .head
    val queryParamsKeys = QueryParamsHelper.extractFromURLString(url).keySet

    queryParamsKeys should contain theSameElementsAs (allScenarioRelatedStatistics ++ notScenarioRelatedStatistics)
    queryParamsKeys should contain theSameElementsAs (emptyScenarioRelatedStatistics ++ notScenarioRelatedStatistics)
  }

  test("should not generate an url if it's not configured") {
    val sut = new UsageStatisticsReportsSettingsService(
      config = UsageStatisticsReportsConfig(enabled = false, errorReportsEnabled = true, None, None),
      fingerprintService = fingerprintService,
      fetchNonArchivedScenariosInputData = () => Future.successful(Right(Nil)),
      fetchActivity = () => Future.successful(Map.empty[String, Int]),
      fetchFeStatistics = () => Future.successful(RawFEStatistics.empty),
      components = List.empty,
      componentUsage = () => Future.successful(Map.empty),
      designerClock = Clock.systemUTC()
    )

    sut.prepareStatisticsUrl().futureValue shouldBe Right(Statistics.Empty)
  }

  test("should include all statisticKeys even without any scenarios created") {
    val url = new UsageStatisticsReportsSettingsService(
      config = UsageStatisticsReportsConfig(
        enabled = true,
        errorReportsEnabled = true,
        Some(sampleFingerprint),
        Some("source")
      ),
      fingerprintService = mockedFingerprintService,
      fetchNonArchivedScenariosInputData = () => Future.successful(Right(List.empty)),
      fetchActivity = () => Future.successful(Map.empty[String, Int]),
      fetchFeStatistics = () => Future.successful(RawFEStatistics.empty),
      components = List.empty,
      componentUsage = () => Future.successful(Map.empty),
      designerClock = Clock.systemUTC()
    ).prepareStatisticsUrl()
      .futureValue
      .value
      .prepareURLs(cfg)
      .map(_.map(_.toString).reduce(_ ++ _))
      .map(url => StatisticEncryptionSupport.decodeToString(url))
      .value

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
      RequestIdStat,
      DesignerUptimeInSeconds,
    )
      .map(_.name)
      .foreach(stat => url should include(stat))
  }

  val nonRunningScenario: ScenarioStatisticsInputData = ScenarioStatisticsInputData(
    isFragment = false,
    ProcessingMode.UnboundedStream,
    DeploymentManagerType("flinkStreaming"),
    Some(SimpleStateStatus.NotDeployed),
    nodesCount = 3,
    scenarioCategory = "Category1",
    scenarioVersion = VersionId(2),
    createdBy = "user",
    fragmentsUsedCount = 1,
    lastDeployedAction = None,
    scenarioId = None
  )

  val runningScenario: ScenarioStatisticsInputData = ScenarioStatisticsInputData(
    isFragment = false,
    ProcessingMode.UnboundedStream,
    DeploymentManagerType("flinkStreaming"),
    Some(SimpleStateStatus.Running),
    nodesCount = 2,
    scenarioCategory = "Category1",
    scenarioVersion = VersionId(3),
    createdBy = "user",
    fragmentsUsedCount = 0,
    lastDeployedAction = None,
    scenarioId = None
  )

  val fragment: ScenarioStatisticsInputData = ScenarioStatisticsInputData(
    isFragment = true,
    ProcessingMode.UnboundedStream,
    DeploymentManagerType("flinkStreaming"),
    None,
    nodesCount = 2,
    scenarioCategory = "Category1",
    scenarioVersion = VersionId(1),
    createdBy = "user",
    fragmentsUsedCount = 0,
    lastDeployedAction = None,
    scenarioId = None
  )

  val k8sRRScenario: ScenarioStatisticsInputData = ScenarioStatisticsInputData(
    isFragment = false,
    ProcessingMode.RequestResponse,
    DeploymentManagerType("lite-k8s"),
    Some(SimpleStateStatus.Running),
    nodesCount = 4,
    scenarioCategory = "Category1",
    scenarioVersion = VersionId(2),
    createdBy = "user",
    fragmentsUsedCount = 2,
    lastDeployedAction = None,
    scenarioId = None
  )

  private def processActivityMap = Map(
    CommentsTotal    -> 4,
    AttachmentsTotal -> 8,
  ).map { case (k, v) => (k.toString, v) }

  private def componentWithImplementation: List[ComponentDefinitionWithImplementation] = List(
    ComponentDefinitionWithImplementation.withEmptyConfig("accountService", TestService),
    ComponentDefinitionWithImplementation.withEmptyConfig("choice", TestService),
    ComponentDefinitionWithImplementation.withEmptyConfig("customService", CustomComponentService),
    ComponentDefinitionWithImplementation.withEmptyConfig("anotherCustomService", CustomComponentService),
  )

  private def componentUsagesMap: Map[DesignerWideComponentId, Long] = Map(
    DesignerWideComponentId("service-accountservice")       -> 5L,
    DesignerWideComponentId("service-customservice")        -> 1L,
    DesignerWideComponentId("service-anothercustomservice") -> 1L,
  )

  object TestService extends Service {

    @MethodToInvoke
    def method(
        @ParamName("paramStringEditor")
        param: String
    ): Future[String] = ???

  }

}
