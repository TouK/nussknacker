package pl.touk.nussknacker.tests.base.it

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.tests.base.db.WithTestDb
import pl.touk.nussknacker.tests.config.WithRichDesignerConfig
import pl.touk.nussknacker.tests.config.WithRichDesignerConfig.TestCategory
import pl.touk.nussknacker.tests.utils.domain.ScenarioHelper

import scala.concurrent.ExecutionContext.Implicits.global

trait WithRichConfigScenarioHelper {
  this: WithTestDb with WithRichDesignerConfig =>

  private val rawScenarioHelper = new ScenarioHelper(testDbRef, designerConfig)

  def createDeployedScenario(scenario: CanonicalProcess, category: TestCategory): ProcessId = {
    rawScenarioHelper.createDeployedScenario(scenario, category.stringify, isFragment = false)
  }

  def createDeployedExampleScenario(scenarioName: ProcessName, category: TestCategory): ProcessId = {
    rawScenarioHelper.createDeployedExampleScenario(scenarioName, category.stringify)
  }

  def createDeployedCanceledExampleScenario(scenarioName: ProcessName, category: TestCategory): ProcessId = {
    rawScenarioHelper.createDeployedCanceledExampleScenario(scenarioName, category.stringify)
  }

}
