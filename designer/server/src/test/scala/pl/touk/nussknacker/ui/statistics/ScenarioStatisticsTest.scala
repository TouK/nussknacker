package pl.touk.nussknacker.ui.statistics

import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.mockito.MockitoSugar.mock
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.component.{
  ComponentGroupName,
  ComponentType,
  DesignerWideComponentId,
  ProcessingMode
}
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.restmodel.component.{ComponentLink, ComponentListElement}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.description.ScenarioActivityApiEndpoints.Dtos.{Attachment, Comment, ScenarioActivity}
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.process.processingtype.DeploymentManagerType
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.ProcessActivity

import java.net.URI
import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ScenarioStatisticsTest
    extends AnyFunSuite
    with Matchers
    with EitherValues
    with PatientScalaFutures
    with TableDrivenPropertyChecks {

  private val sampleFingerprint = "fooFingerprint"

  private val mockedFingerprintService: FingerprintService = mock[FingerprintService](
    new Answer[Future[Either[StatisticError, Fingerprint]]] {
      override def answer(invocation: InvocationOnMock): Future[Either[StatisticError, Fingerprint]] =
        Future.successful(Right(new Fingerprint(sampleFingerprint)))
    }
  )

  test("should determine statistics for running scenario with streaming processing mode and flink engine") {
    val scenarioData = ScenarioStatisticsInputData(
      isFragment = false,
      ProcessingMode.UnboundedStream,
      DeploymentManagerType("flinkStreaming"),
      Some(SimpleStateStatus.Running),
      nodesCount = 2,
      scenarioCategory = "Category1",
      scenarioVersion = VersionId(2),
      createdBy = "user",
      fragmentsUsedCount = 0,
      lastDeployedAction = None,
      scenarioId = None
    )
    val statistics = ScenarioStatistics.determineStatisticsForScenario(scenarioData)
    statistics shouldEqual Map(
      "s_s"     -> 1,
      "s_f"     -> 0,
      "s_pm_s"  -> 1,
      "s_pm_b"  -> 0,
      "s_pm_rr" -> 0,
      "s_dm_f"  -> 1,
      "s_dm_l"  -> 0,
      "s_dm_e"  -> 0,
      "s_dm_c"  -> 0,
      "s_a"     -> 1,
    )
  }

  test("should determine statistics for scenario vs fragment") {
    def scenarioData(isFragment: Boolean) = ScenarioStatisticsInputData(
      isFragment = isFragment,
      ProcessingMode.UnboundedStream,
      DeploymentManagerType("foo"),
      status = None,
      nodesCount = 2,
      scenarioCategory = "Category1",
      scenarioVersion = VersionId(2),
      createdBy = "user",
      fragmentsUsedCount = 0,
      lastDeployedAction = None,
      scenarioId = None
    )

    forAll(
      Table(
        ("isFragment", "expectedScenarioFragmentStats"),
        (false, Map("s_s" -> 1, "s_f" -> 0)),
        (true, Map("s_s" -> 0, "s_f" -> 1)),
      )
    ) { (isFragment, expectedScenarioFragmentStats) =>
      val resultStatistics =
        ScenarioStatistics.determineStatisticsForScenario(scenarioData(isFragment))
      resultStatistics should contain allElementsOf expectedScenarioFragmentStats
    }
  }

  test("should determine statistics for processing mode") {
    def scenarioData(processingMode: ProcessingMode) = ScenarioStatisticsInputData(
      isFragment = false,
      processingMode,
      DeploymentManagerType("foo"),
      status = None,
      nodesCount = 2,
      scenarioCategory = "Category1",
      scenarioVersion = VersionId(2),
      createdBy = "user",
      fragmentsUsedCount = 0,
      lastDeployedAction = None,
      scenarioId = None
    )

    forAll(
      Table(
        ("processingMode", "expectedProcessingModeStats"),
        (ProcessingMode.UnboundedStream, Map("s_pm_s" -> 1, "s_pm_b" -> 0, "s_pm_rr" -> 0)),
        (ProcessingMode.BoundedStream, Map("s_pm_s" -> 0, "s_pm_b" -> 1, "s_pm_rr" -> 0)),
        (ProcessingMode.RequestResponse, Map("s_pm_s" -> 0, "s_pm_b" -> 0, "s_pm_rr" -> 1)),
      )
    ) { (processingMode, expectedProcessingModeStats) =>
      val resultStatistics =
        ScenarioStatistics.determineStatisticsForScenario(scenarioData(processingMode))
      resultStatistics should contain allElementsOf expectedProcessingModeStats
    }
  }

  test("should determine statistics for deployment manager") {
    def scenarioData(dmType: DeploymentManagerType) = ScenarioStatisticsInputData(
      isFragment = false,
      ProcessingMode.UnboundedStream,
      dmType,
      status = None,
      nodesCount = 2,
      scenarioCategory = "Category1",
      scenarioVersion = VersionId(2),
      createdBy = "user",
      fragmentsUsedCount = 0,
      lastDeployedAction = None,
      scenarioId = None
    )

    forAll(
      Table(
        ("dmType", "expectedProcessingModeStats"),
        (DeploymentManagerType("flinkStreaming"), Map("s_dm_f" -> 1, "s_dm_l" -> 0, "s_dm_e" -> 0, "s_dm_c" -> 0)),
        (DeploymentManagerType("lite-k8s"), Map("s_dm_f" -> 0, "s_dm_l" -> 1, "s_dm_e" -> 0, "s_dm_c" -> 0)),
        (DeploymentManagerType("lite-embedded"), Map("s_dm_f" -> 0, "s_dm_l" -> 0, "s_dm_e" -> 1, "s_dm_c" -> 0)),
        (DeploymentManagerType("custom "), Map("s_dm_f" -> 0, "s_dm_l" -> 0, "s_dm_e" -> 0, "s_dm_c" -> 1)),
      )
    ) { (dmType, expectedDMStats) =>
      val resultStatistics =
        ScenarioStatistics.determineStatisticsForScenario(scenarioData(dmType))
      resultStatistics should contain allElementsOf expectedDMStats
    }
  }

  test("should determine statistics for status") {
    def scenarioData(status: Option[StateStatus]) = ScenarioStatisticsInputData(
      isFragment = false,
      ProcessingMode.UnboundedStream,
      DeploymentManagerType("foo"),
      status,
      nodesCount = 2,
      scenarioCategory = "Category1",
      scenarioVersion = VersionId(2),
      createdBy = "user",
      fragmentsUsedCount = 0,
      lastDeployedAction = None,
      scenarioId = None
    )

    ScenarioStatistics.determineStatisticsForScenario(scenarioData(None))(
      "s_a"
    ) shouldBe 0
    ScenarioStatistics.determineStatisticsForScenario(
      scenarioData(Some(SimpleStateStatus.NotDeployed))
    )(
      "s_a"
    ) shouldBe 0
    ScenarioStatistics.determineStatisticsForScenario(
      scenarioData(Some(SimpleStateStatus.DuringDeploy))
    )(
      "s_a"
    ) shouldBe 0
    ScenarioStatistics.determineStatisticsForScenario(
      scenarioData(Some(SimpleStateStatus.Running))
    )(
      "s_a"
    ) shouldBe 1
    ScenarioStatistics.determineStatisticsForScenario(
      scenarioData(Some(SimpleStateStatus.Canceled))
    )(
      "s_a"
    ) shouldBe 0
  }

  test("should determine query params with version and source ") {
    val urlStrings = new UsageStatisticsReportsSettingsService(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      StatisticUrlConfig(),
      mockedFingerprintService,
      () => Future.successful(Right(List.empty)),
      _ => Future.successful(Right(List.empty)),
      () => Future.successful(Right(List.empty)),
      () => Future.successful(Map.empty[String, Long]),
      List.empty
    ).prepareStatisticsUrl().futureValue.value

    urlStrings.length shouldEqual 1
    val urlString = urlStrings.head
    urlString should include(s"fingerprint=$sampleFingerprint")
    urlString should include("source=sources")
    urlString should include(s"version=${BuildInfo.version}")
  }

  test("should determine statistics for components") {
    val params = new UsageStatisticsReportsSettingsService(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      StatisticUrlConfig(),
      mockedFingerprintService,
      () => Future.successful(Right(List.empty)),
      _ => Future.successful(Right(List.empty)),
      () => Future.successful(Right(componentList)),
      () => Future.successful(Map.empty[String, Long]),
      componentWithImplementation
    ).determineQueryParams().value.futureValue.value

    params should contain("c_srvcccntsrvc" -> "5")
    params should contain("c_cstm" -> "1")
    params.keySet shouldNot contain("c_bltnchc")
  }

  test("should combined statistics for all scenarios") {
    val nonRunningScenario = ScenarioStatisticsInputData(
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
    val runningScenario = ScenarioStatisticsInputData(
      isFragment = false,
      ProcessingMode.UnboundedStream,
      DeploymentManagerType("flinkStreaming"),
      Some(SimpleStateStatus.Running),
      nodesCount = 2,
      scenarioCategory = "Category1",
      scenarioVersion = VersionId(2),
      createdBy = "user",
      fragmentsUsedCount = 0,
      lastDeployedAction = None,
      scenarioId = None
    )
    val fragment = ScenarioStatisticsInputData(
      isFragment = true,
      ProcessingMode.UnboundedStream,
      DeploymentManagerType("flinkStreaming"),
      None,
      nodesCount = 2,
      scenarioCategory = "Category1",
      scenarioVersion = VersionId(2),
      createdBy = "user",
      fragmentsUsedCount = 0,
      lastDeployedAction = None,
      scenarioId = None
    )
    val k8sRRScenario = ScenarioStatisticsInputData(
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

    val params = new UsageStatisticsReportsSettingsService(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      StatisticUrlConfig(),
      mockedFingerprintService,
      () => Future.successful(Right(List(nonRunningScenario, runningScenario, fragment, k8sRRScenario))),
      _ => Future.successful(Right(processActivityList)),
      () => Future.successful(Right(componentList)),
      () => Future.successful(Map.empty[String, Long]),
      componentWithImplementation
    ).determineQueryParams().value.futureValue.value

    val expectedStats = Map(
      AuthorsCount           -> "1",
      AttachmentsTotal       -> "1",
      AttachmentsAverage     -> "1",
      CategoriesCount        -> "1",
      CommentsTotal          -> "1",
      CommentsAverage        -> "1",
      VersionsMedian         -> "2",
      VersionsMax            -> "2",
      VersionsMin            -> "2",
      VersionsAverage        -> "2",
      UptimeInSecondsAverage -> "0",
      UptimeInSecondsMax     -> "0",
      UptimeInSecondsMin     -> "0",
      ComponentsCount        -> "3",
      FragmentsUsedMedian    -> "1",
      FragmentsUsedAverage   -> "1",
      NodesMedian            -> "3",
      NodesAverage           -> "2",
      NodesMax               -> "2",
      NodesMin               -> "4",
      ScenarioCount          -> "3",
      FragmentCount          -> "1",
      UnboundedStreamCount   -> "3",
      BoundedStreamCount     -> "0",
      RequestResponseCount   -> "1",
      FlinkDMCount           -> "3",
      LiteK8sDMCount         -> "1",
      LiteEmbeddedDMCount    -> "0",
      UnknownDMCount         -> "0",
      ActiveScenarioCount    -> "2",
      "c_srvcccntsrvc"       -> "5",
      "c_cstm"               -> "1",
    ).map { case (k, v) => (k.toString, v) }
    params should contain allElementsOf expectedStats
  }

  private def processActivityList = {
    val scenarioActivity: ScenarioActivity = ScenarioActivity(
      comments = List(
        Comment(
          id = 1L,
          processVersionId = 1L,
          content = "some comment",
          user = "test",
          createDate = Instant.parse("2024-01-17T14:21:17Z")
        )
      ),
      attachments = List(
        Attachment(
          id = 1L,
          processVersionId = 1L,
          fileName = "some_file.txt",
          user = "test",
          createDate = Instant.parse("2024-01-17T14:21:17Z")
        )
      )
    )
    List(
      ProcessActivity(
        scenarioActivity.comments.map(comment =>
          DbProcessActivityRepository.Comment(
            comment.id,
            VersionId(comment.processVersionId),
            comment.content,
            comment.user,
            comment.createDate
          )
        ),
        scenarioActivity.attachments.map(attachment =>
          DbProcessActivityRepository.Attachment(
            attachment.id,
            VersionId(attachment.processVersionId),
            attachment.fileName,
            attachment.user,
            attachment.createDate
          )
        )
      )
    )
  }

  private val componentList = List(
    ComponentListElement(
      DesignerWideComponentId("streaming-dev-service-accountservice"),
      "accountService",
      "/assets/components/Processor.svg",
      ComponentType.Service,
      ComponentGroupName("services"),
      List("Category1"),
      links = List.empty,
      usageCount = 3,
      AllowedProcessingModes.SetOf(ProcessingMode.UnboundedStream)
    ),
    ComponentListElement(
      DesignerWideComponentId("request-response-service-accountservice"),
      "accountService",
      "/assets/components/Processor.svg",
      ComponentType.Service,
      ComponentGroupName("services"),
      List("Category1"),
      links = List.empty,
      usageCount = 2,
      AllowedProcessingModes.SetOf(ProcessingMode.RequestResponse)
    ),
    ComponentListElement(
      DesignerWideComponentId("builtin-choice"),
      "choice",
      "/assets/components/Switch.svg",
      ComponentType.BuiltIn,
      ComponentGroupName("base"),
      List(
        "BatchDev",
        "Category1",
        "Category2",
        "Default",
        "DevelopmentTests",
        "Periodic",
        "RequestResponse",
        "RequestResponseK8s",
        "StreamingLite",
        "StreamingLiteK8s"
      ),
      List(
        ComponentLink(
          "documentation",
          "Documentation",
          new URI("/assets/icons/documentation.svg"),
          new URI("https://nussknacker.io/documentation/docs/scenarios_authoring/BasicNodes#choice")
        )
      ),
      0,
      AllowedProcessingModes.All
    ),
    ComponentListElement(
      DesignerWideComponentId("someCustomComponent"),
      "someCustomComponent",
      "icon",
      ComponentType.Service,
      ComponentGroupName("someCustomGroup"),
      List("Streaming"),
      List.empty,
      1,
      AllowedProcessingModes.SetOf(ProcessingMode.UnboundedStream)
    )
  )

  private val componentWithImplementation: List[ComponentDefinitionWithImplementation] = List(
    ComponentDefinitionWithImplementation.withEmptyConfig("accountService", TestService)
  )

  object TestService extends Service {

    @MethodToInvoke
    def method(
        @ParamName("paramStringEditor")
        param: String
    ): Future[String] = ???

  }

}
