package pl.touk.nussknacker.engine.definition.test

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.SourceNodeData

trait TestInfoProvider {

  def getTestingCapabilities(processVersion: ProcessVersion, scenario: CanonicalProcess): TestingCapabilities

  def getTestParameters(processVersion: ProcessVersion, scenario: CanonicalProcess): Map[String, List[Parameter]]

  def generateTestData(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess,
      size: Int
  ): Either[String, PreliminaryScenarioTestData]

  def prepareTestData(
      preliminaryTestData: PreliminaryScenarioTestData,
      scenario: CanonicalProcess
  ): Either[String, ScenarioTestData]

  def generateTestDataForSource(
      metaData: MetaData,
      sourceNodeData: SourceNodeData,
      size: Int
  ): Either[String, PreliminaryScenarioTestData]

}
