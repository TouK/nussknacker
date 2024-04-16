package pl.touk.nussknacker.ui.statistics

import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.mockito.MockitoSugar.mock
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentType, DesignerWideComponentId, ProcessingMode}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.restmodel.component.ComponentListElement
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.description.ScenarioActivityApiEndpoints.Dtos.{Attachment, Comment, ScenarioActivity}
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.process.processingtype.DeploymentManagerType
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.ProcessActivity

import java.time.Instant
import java.util.UUID
import scala.collection.immutable.{ListMap, Map, TreeMap}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UsageStatisticsReportsSettingsDeterminerTest
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

  test("should generate correct url with encoded params") {
    UsageStatisticsReportsSettingsDeterminer.prepareUrlString(
      ListMap("f" -> "a b", "v" -> "1.6.5-a&b=c")
    ) shouldBe "https://stats.nussknacker.io/?f=a+b&v=1.6.5-a%26b%3Dc"
  }

  test("should determine query params with version and source ") {
    val params = new UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      mockedFingerprintService,
      () => Future.successful(Right(List.empty)),
      () => Future.successful(Right(List.empty)),
      () => Future.successful(Right(List.empty)),
      () => Future.successful(Right(List.empty))
    ).determineQueryParams().value.futureValue.value
    params should contain("fingerprint" -> sampleFingerprint)
    params should contain("source" -> "sources")
    params should contain("version" -> BuildInfo.version)
  }

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
      fragmentsUsedCount = 0
    )
    val statistics = UsageStatisticsReportsSettingsDeterminer.determineStatisticsForScenario(scenarioData)
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
      fragmentsUsedCount = 0
    )

    forAll(
      Table(
        ("isFragment", "expectedScenarioFragmentStats"),
        (false, Map("s_s" -> 1, "s_f" -> 0)),
        (true, Map("s_s" -> 0, "s_f" -> 1)),
      )
    ) { (isFragment, expectedScenarioFragmentStats) =>
      val resultStatistics =
        UsageStatisticsReportsSettingsDeterminer.determineStatisticsForScenario(scenarioData(isFragment))
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
      fragmentsUsedCount = 0
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
        UsageStatisticsReportsSettingsDeterminer.determineStatisticsForScenario(scenarioData(processingMode))
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
      fragmentsUsedCount = 0
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
        UsageStatisticsReportsSettingsDeterminer.determineStatisticsForScenario(scenarioData(dmType))
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
      fragmentsUsedCount = 0
    )

    UsageStatisticsReportsSettingsDeterminer.determineStatisticsForScenario(scenarioData(None))(
      "s_a"
    ) shouldBe 0
    UsageStatisticsReportsSettingsDeterminer.determineStatisticsForScenario(
      scenarioData(Some(SimpleStateStatus.NotDeployed))
    )(
      "s_a"
    ) shouldBe 0
    UsageStatisticsReportsSettingsDeterminer.determineStatisticsForScenario(
      scenarioData(Some(SimpleStateStatus.DuringDeploy))
    )(
      "s_a"
    ) shouldBe 0
    UsageStatisticsReportsSettingsDeterminer.determineStatisticsForScenario(
      scenarioData(Some(SimpleStateStatus.Running))
    )(
      "s_a"
    ) shouldBe 1
    UsageStatisticsReportsSettingsDeterminer.determineStatisticsForScenario(
      scenarioData(Some(SimpleStateStatus.Canceled))
    )(
      "s_a"
    ) shouldBe 0
  }

  test("should combined statistics for all scenarios") {
    val nonRunningScenario = ScenarioStatisticsInputData(
      isFragment = false,
      ProcessingMode.UnboundedStream,
      DeploymentManagerType("flinkStreaming"),
      Some(SimpleStateStatus.NotDeployed),
      nodesCount = 2,
      scenarioCategory = "Category1",
      scenarioVersion = VersionId(2),
      createdBy = "user",
      fragmentsUsedCount = 0
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
      fragmentsUsedCount = 0
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
      fragmentsUsedCount = 0
    )
    val k8sRRScenario = ScenarioStatisticsInputData(
      isFragment = false,
      ProcessingMode.RequestResponse,
      DeploymentManagerType("lite-k8s"),
      Some(SimpleStateStatus.Running),
      nodesCount = 2,
      scenarioCategory = "Category1",
      scenarioVersion = VersionId(2),
      createdBy = "user",
      fragmentsUsedCount = 0
    )

    val params = new UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      mockedFingerprintService,
      () => Future.successful(Right(List(nonRunningScenario, runningScenario, fragment, k8sRRScenario))),
      // TODO need to provide proper sample data
      () => Future.successful(Right(processActivityList)),
      () => Future.successful(Right(componentList)),
      () => Future.successful(Right(listOfProcessActions))
    ).determineQueryParams().value.futureValue.value

    val expectedStats = Map(
      "a_n"     -> "1",
      "a_t"     -> "1",
      "a_v"     -> "1",
      "c"       -> "1",
      "c_n"     -> "1",
      "c_v"     -> "1",
      "v_m"     -> "2",
      "v_ma"    -> "2",
      "v_mi"    -> "2",
      "v_v"     -> "2",
      "u_v"     -> "3832067",
      "u_ma"    -> "3832067",
      "u_mi"    -> "3832067",
      "c_t"     -> "1",
      "f_m"     -> "0",
      "f_v"     -> "0",
      "n_m"     -> "2",
      "n_v"     -> "2",
      "n_ma"    -> "2",
      "n_mi"    -> "2",
      "s_s"     -> "3",
      "s_f"     -> "1",
      "s_pm_s"  -> "3",
      "s_pm_b"  -> "0",
      "s_pm_rr" -> "1",
      "s_dm_f"  -> "3",
      "s_dm_l"  -> "1",
      "s_dm_e"  -> "0",
      "s_dm_c"  -> "0",
      "s_a"     -> "2",
    )
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
      AllowedProcessingModes.SetOf(ProcessingMode.RequestResponse)
    )
  )

  private val listOfProcessActions = List(
    List(
      ProcessAction(
        ProcessActionId(UUID.fromString("bb698fd8-b7aa-4601-bcbf-7347ff66aefc")),
        ProcessId(2),
        VersionId(2),
        "user",
        Instant.parse("2024-01-17T14:21:17Z"),
        Instant.parse("2024-01-17T14:21:17Z"),
        ScenarioActionName.Deploy,
        ProcessActionState.Finished,
        None,
        Some(1),
        Some("deploy comment"),
        TreeMap(
          "engine-version"  -> "0.1",
          "generation-time" -> "2024-04-15T09:15:12.436321",
          "process-version" -> "0.1"
        )
      ),
      ProcessAction(
        ProcessActionId(UUID.fromString("bb698fd8-b7aa-4601-bcbf-7347ff66aefc")),
        ProcessId(2),
        VersionId(2),
        "user",
        Instant.parse("2024-04-15T07:16:42.518161Z"),
        Instant.parse("2024-04-15T07:16:51.557321Z"),
        ScenarioActionName.Cancel,
        ProcessActionState.Finished,
        None,
        Some(1),
        Some("deploy comment"),
        TreeMap(
          "engine-version"  -> "0.1",
          "generation-time" -> "2024-04-15T09:15:12.436321",
          "process-version" -> "0.1"
        )
      )
    )
  )

}
