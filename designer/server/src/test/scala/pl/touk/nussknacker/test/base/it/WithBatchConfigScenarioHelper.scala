package pl.touk.nussknacker.test.base.it

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.base.db.WithTestDb
import pl.touk.nussknacker.test.config.WithBatchDesignerConfig
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestCategory
import pl.touk.nussknacker.test.utils.domain.ScenarioHelper

import scala.concurrent.ExecutionContext.Implicits.global

trait WithBatchConfigScenarioHelper {
  this: WithTestDb with WithClock with WithBatchDesignerConfig =>

  private lazy val rawScenarioHelper = new ScenarioHelper(testDbRef, clock, designerConfig)
  private val usedCategory           = TestCategory.Category1

  def createSavedScenario(scenario: CanonicalProcess): ProcessId = {
    rawScenarioHelper.createSavedScenario(scenario, usedCategory.stringify, isFragment = false)
  }

  def createSavedFragment(fragment: CanonicalProcess): ProcessId = {
    rawScenarioHelper.createSavedScenario(fragment, usedCategory.stringify, isFragment = true)
  }

  def archiveScenario(idWithName: ProcessIdWithName): Unit = {
    rawScenarioHelper.archiveScenario(idWithName)
  }

}
