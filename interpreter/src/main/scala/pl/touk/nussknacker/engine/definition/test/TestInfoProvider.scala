package pl.touk.nussknacker.engine.definition.test

import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

trait TestInfoProvider {

  def getTestingCapabilities(scenario: CanonicalProcess): TestingCapabilities

  def generateTestData(scenario: CanonicalProcess, size: Int): Option[PreliminaryScenarioTestData]

  def prepareTestData(preliminaryTestData: PreliminaryScenarioTestData, scenario: CanonicalProcess): Either[String, ScenarioTestData]

}
