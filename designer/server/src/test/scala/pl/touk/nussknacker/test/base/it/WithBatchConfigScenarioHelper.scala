package pl.touk.nussknacker.test.base.it

import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.base.db.WithTestDb
import pl.touk.nussknacker.test.config.WithBatchDesignerConfig
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestCategory
import pl.touk.nussknacker.test.utils.domain.ScenarioHelper

import scala.concurrent.ExecutionContext.Implicits.global

trait WithBatchConfigScenarioHelper {
  this: WithTestDb with WithBatchDesignerConfig =>

  private lazy val rawScenarioHelper = new ScenarioHelper(testDbRef, designerConfig)
  private val usedCategory           = TestCategory.Category1

  def createSavedScenario(scenario: CanonicalProcess): ProcessId = {
    rawScenarioHelper.createSavedScenario(scenario, usedCategory.stringify, isFragment = false)
  }

}
