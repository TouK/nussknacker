package pl.touk.nussknacker.ui.statistics

import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.process.processingtype.DeploymentManagerType

class ScenarioStatisticsTest
    extends AnyFunSuite
    with Matchers
    with EitherValues
    with PatientScalaFutures
    with TableDrivenPropertyChecks {

  test("should determine statistics for running scenario with streaming processing mode and flink engine") {
    val scenarioData = ScenarioStatisticsInputData(
      isFragment = false,
      ProcessingMode.UnboundedStream,
      DeploymentManagerType("flinkStreaming"),
      Some(SimpleStateStatus.Running.name),
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
      status.map(_.name),
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

}
