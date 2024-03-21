package pl.touk.nussknacker.ui.statistics

import org.apache.commons.io.FileUtils
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.process.processingtype.DeploymentManagerType

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.collection.immutable.{ListMap, Map}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UsageStatisticsReportsSettingsDeterminerTest
    extends AnyFunSuite
    with Matchers
    with OptionValues
    with PatientScalaFutures
    with TableDrivenPropertyChecks {

  private val sampleFingerprint = "fooFingerprint"

  test("should generate correct url with encoded params") {
    UsageStatisticsReportsSettingsDeterminer.prepareUrl(
      ListMap("f" -> "a b", "v" -> "1.6.5-a&b=c")
    ) shouldBe "https://stats.nussknacker.io/?f=a+b&v=1.6.5-a%26b%3Dc"
  }

  test("should determine query params with version and source ") {
    val fingerprintFile = getTempFileLocation
    val params = new UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      fingerprintFile,
      () => Future.successful(List.empty)
    ).determineQueryParams().futureValue
    params should contain("fingerprint" -> sampleFingerprint)
    params should contain("source" -> "sources")
    params should contain("version" -> BuildInfo.version)
    fingerprintFile.exists() shouldBe false
  }

  test("should determine random fingerprint if configured is blank") {
    val fingerprintFile = getTempFileLocation
    val params = new UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(""), None),
      fingerprintFile,
      () => Future.successful(List.empty)
    ).determineQueryParams().futureValue
    params("fingerprint") should startWith("gen-")
    fingerprintFile.exists() shouldBe true
  }

  test("should read persisted fingerprint") {
    val fingerprintFile = File.createTempFile("nussknacker", ".fingerprint")
    fingerprintFile.deleteOnExit()
    val savedFingerprint = "foobarbaz123"
    FileUtils.writeStringToFile(fingerprintFile, savedFingerprint, StandardCharsets.UTF_8)

    val params = new UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, None, None),
      fingerprintFile,
      () => Future.successful(List.empty)
    ).determineQueryParams().futureValue
    params.get("fingerprint").value shouldEqual savedFingerprint
  }

  test("should determine statistics for running scenario with streaming processing mode and flink engine") {
    val scenarioData = ScenarioStatisticsInputData(
      isFragment = false,
      ProcessingMode.UnboundedStream,
      DeploymentManagerType("flinkStreaming"),
      Some(SimpleStateStatus.Running)
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
      None
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
      None
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
      None
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
      status
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
      Some(SimpleStateStatus.NotDeployed)
    )
    val runningScenario = ScenarioStatisticsInputData(
      isFragment = false,
      ProcessingMode.UnboundedStream,
      DeploymentManagerType("flinkStreaming"),
      Some(SimpleStateStatus.Running)
    )
    val fragment = ScenarioStatisticsInputData(
      isFragment = true,
      ProcessingMode.UnboundedStream,
      DeploymentManagerType("flinkStreaming"),
      None
    )
    val k8sRRScenario = ScenarioStatisticsInputData(
      isFragment = false,
      ProcessingMode.RequestResponse,
      DeploymentManagerType("lite-k8s"),
      Some(SimpleStateStatus.Running)
    )

    val fingerprintFile = getTempFileLocation
    val params = new UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      fingerprintFile,
      () => Future.successful(List(nonRunningScenario, runningScenario, fragment, k8sRRScenario))
    ).determineQueryParams().futureValue

    val expectedStats = Map(
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

  private def getTempFileLocation: File = {
    val file = new File(System.getProperty("java.io.tmpdir"), s"nussknacker-${UUID.randomUUID()}.fingerprint")
    file.deleteOnExit()
    file
  }

}
