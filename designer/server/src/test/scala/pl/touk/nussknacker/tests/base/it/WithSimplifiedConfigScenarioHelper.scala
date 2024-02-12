package pl.touk.nussknacker.tests.base.it

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.tests.base.db.WithTestDb
import pl.touk.nussknacker.tests.config.WithSimplifiedNuConfig
import pl.touk.nussknacker.tests.config.WithSimplifiedNuConfig.TestCategory
import pl.touk.nussknacker.tests.utils.domain.ScenarioHelper

import scala.concurrent.ExecutionContext.Implicits.global

trait WithSimplifiedConfigScenarioHelper {
  this: WithTestDb with WithSimplifiedNuConfig =>

  private val rawScenarioHelper = new ScenarioHelper(testDbRef, designerConfig)
  private val usedCategory      = TestCategory.Default

  def createDeployedScenario(scenario: CanonicalProcess): ProcessId = {
    rawScenarioHelper.createDeployedScenario(scenario, usedCategory.stringify, isFragment = false)
  }

  def createDeployedExampleScenario(scenarioName: ProcessName): ProcessId = {
    rawScenarioHelper.createDeployedExampleScenario(scenarioName, usedCategory.stringify)
  }

  def createDeployedCanceledExampleScenario(scenarioName: ProcessName): ProcessId = {
    rawScenarioHelper.createDeployedCanceledExampleScenario(scenarioName, usedCategory.stringify)
  }

}
