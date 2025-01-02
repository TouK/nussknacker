package pl.touk.nussknacker.test.base.it

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.base.db.WithTestDb
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory
import pl.touk.nussknacker.test.utils.domain.ScenarioHelper

import scala.concurrent.ExecutionContext.Implicits.global

trait WithAccessControlCheckingConfigScenarioHelper {
  this: WithTestDb with WithClock with WithAccessControlCheckingDesignerConfig =>

  private lazy val rawScenarioHelper = new ScenarioHelper(testDbRef, clock, designerConfig)

  def createEmptyScenario(scenarioName: ProcessName, category: TestCategory): ProcessId = {
    rawScenarioHelper.createEmptyScenario(scenarioName, category.stringify, isFragment = false)
  }

  def createEmptyFragment(fragmentName: ProcessName, category: TestCategory): ProcessId = {
    rawScenarioHelper.createEmptyScenario(fragmentName, category.stringify, isFragment = true)
  }

  def createSavedScenario(scenario: CanonicalProcess, category: TestCategory): ProcessId = {
    rawScenarioHelper.createSavedScenario(scenario, category.stringify, isFragment = false)
  }

  def createSavedFragment(scenario: CanonicalProcess, category: TestCategory): ProcessId = {
    rawScenarioHelper.createSavedScenario(scenario, category.stringify, isFragment = true)
  }

  def createDeployedScenario(scenario: CanonicalProcess, category: TestCategory): ProcessId = {
    rawScenarioHelper.createDeployedScenario(scenario, category.stringify, isFragment = false)
  }

  def createDeployedExampleScenario(scenarioName: ProcessName, category: TestCategory): ProcessId = {
    rawScenarioHelper.createDeployedExampleScenario(scenarioName, category.stringify, isFragment = false)
  }

  def createArchivedExampleScenario(scenarioName: ProcessName, category: TestCategory): ProcessId = {
    rawScenarioHelper.createArchivedExampleScenario(scenarioName, category.stringify, isFragment = false)
  }

  def createArchivedExampleFragment(fragmentName: ProcessName, category: TestCategory): ProcessId = {
    rawScenarioHelper.createArchivedExampleScenario(fragmentName, category.stringify, isFragment = true)
  }

  def createDeployedCanceledExampleScenario(scenarioName: ProcessName, category: TestCategory): ProcessId = {
    rawScenarioHelper.createDeployedCanceledExampleScenario(scenarioName, category.stringify, isFragment = false)
  }

  def createDeployedScenario(scenarioName: ProcessName, category: TestCategory): ProcessId = {
    rawScenarioHelper.createDeployedScenario(scenarioName, category.stringify, isFragment = false)
  }

}
