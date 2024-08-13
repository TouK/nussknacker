package pl.touk.nussknacker.test.base.it

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.base.db.WithTestDb
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestCategory
import pl.touk.nussknacker.test.utils.domain.ScenarioHelper

import scala.concurrent.ExecutionContext.Implicits.global

trait WithCategoryUsedMoreThanOnceConfigScenarioHelper {
  this: WithTestDb with WithCategoryUsedMoreThanOnceDesignerConfig =>

  private lazy val rawScenarioHelper = new ScenarioHelper(testDbRef, designerConfig)
  private val usedCategory           = TestCategory.Category1

  def createSavedScenario(scenario: CanonicalProcess): ProcessId = {
    rawScenarioHelper.createSavedScenario(scenario, usedCategory.stringify, isFragment = false)
  }

  def createArchivedExampleScenario(scenarioName: ProcessName): ProcessId = {
    rawScenarioHelper.createArchivedExampleScenario(scenarioName, usedCategory.stringify, isFragment = false)
  }

  def createSavedFragment(scenario: CanonicalProcess): ProcessId = {
    rawScenarioHelper.createSavedScenario(scenario, usedCategory.stringify, isFragment = true)
  }

}
