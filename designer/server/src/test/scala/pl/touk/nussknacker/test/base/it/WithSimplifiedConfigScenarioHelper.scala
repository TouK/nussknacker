package pl.touk.nussknacker.test.base.it

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.base.db.WithTestDb
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestCategory
import pl.touk.nussknacker.test.utils.domain.ScenarioHelper

import scala.concurrent.ExecutionContext.Implicits.global

trait WithSimplifiedConfigScenarioHelper {
  this: WithTestDb with WithSimplifiedDesignerConfig =>

  private val rawScenarioHelper = new ScenarioHelper(testDbRef, designerConfig)
  private val usedCategory      = TestCategory.Category1

  def createSavedScenario(scenario: CanonicalProcess): ProcessId = {
    rawScenarioHelper.createSavedScenario(scenario, usedCategory.stringify, isFragment = false)
  }

  def createDeployedScenario(scenario: CanonicalProcess): ProcessId = {
    rawScenarioHelper.createDeployedScenario(scenario, usedCategory.stringify, isFragment = false)
  }

  def createDeployedExampleScenario(scenarioName: ProcessName): ProcessId = {
    rawScenarioHelper.createDeployedExampleScenario(scenarioName, usedCategory.stringify, isFragment = false)
  }

  def createDeployedCanceledExampleScenario(scenarioName: ProcessName): ProcessId = {
    rawScenarioHelper.createDeployedCanceledExampleScenario(scenarioName, usedCategory.stringify, isFragment = false)
  }

}
