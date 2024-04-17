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
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.restmodel.component.ComponentListElement
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.description.ScenarioActivityApiEndpoints.Dtos.{Attachment, Comment, ScenarioActivity}
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.process.processingtype.DeploymentManagerType
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.ProcessActivity

import java.time.Instant
import scala.collection.immutable.{ListMap, Map}
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
      _ => Future.successful(Right(List.empty)),
      () => Future.successful(Right(List.empty))
    ).determineQueryParams().value.futureValue.value
    params should contain("fingerprint" -> sampleFingerprint)
    params should contain("source" -> "sources")
    params should contain("version" -> BuildInfo.version)
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
      fragmentsUsedCount = 0,
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
      nodesCount = 2,
      scenarioCategory = "Category1",
      scenarioVersion = VersionId(2),
      createdBy = "user",
      fragmentsUsedCount = 0,
      lastDeployedAction = None,
      scenarioId = None
    )

    val params = new UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      mockedFingerprintService,
      () => Future.successful(Right(List(nonRunningScenario, runningScenario, fragment, k8sRRScenario))),
      _  => Future.successful(Right(processActivityList)),
      () => Future.successful(Right(componentList)),
    ).determineQueryParams().value.futureValue.value

    val expectedStats = Map(
      AuthorsCount       -> "1",
      AttachmentsTotal   -> "1",
      AttachmentsAverage -> "1",
      CategoriesCount    -> "1",
      CommentsTotal      -> "1",
      CommentsAverage    -> "1",
      VersionsMedian     -> "2",
      VersionsMax        -> "2",
      VersionsMin        -> "2",
      VersionsAverage    -> "2",
      UptimeAverage      -> "0",
      UptimeMax          -> "0",
      UptimeMin          -> "0",
      ComponentsCount    -> "1",
      FragmentsMedian    -> "0",
      FragmentsAverage   -> "0",
      NodesMedian        -> "2",
      NodesAverage       -> "2",
      NodesMax           -> "2",
      NodesMin           -> "2",
      "s_s"              -> "3",
      "s_f"              -> "1",
      "s_pm_s"           -> "3",
      "s_pm_b"           -> "0",
      "s_pm_rr"          -> "1",
      "s_dm_f"           -> "3",
      "s_dm_l"           -> "1",
      "s_dm_e"           -> "0",
      "s_dm_c"           -> "0",
      "s_a"              -> "2",
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
      AllowedProcessingModes.SetOf(ProcessingMode.RequestResponse)
    )
  )

}
