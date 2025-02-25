package pl.touk.nussknacker.engine.definition.test

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.test.TestInfoProvider.{
  ScenarioTestDataGenerationError,
  SourceTestDataGenerationError,
  TestDataPreparationError
}
import pl.touk.nussknacker.engine.graph.node.SourceNodeData

trait TestInfoProvider {

  def getTestingCapabilities(processVersion: ProcessVersion, scenario: CanonicalProcess): TestingCapabilities

  def getTestParameters(processVersion: ProcessVersion, scenario: CanonicalProcess): Map[String, List[Parameter]]

  def generateTestData(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess,
      size: Int
  ): Either[ScenarioTestDataGenerationError, PreliminaryScenarioTestData]

  def prepareTestData(
      preliminaryTestData: PreliminaryScenarioTestData,
      scenario: CanonicalProcess
  ): Either[TestDataPreparationError, ScenarioTestData]

  def generateTestDataForSource(
      metaData: MetaData,
      sourceNodeData: SourceNodeData,
      size: Int
  ): Either[SourceTestDataGenerationError, PreliminaryScenarioTestData]

}

object TestInfoProvider {

  sealed trait TestDataError

  sealed trait SourceTestDataGenerationError extends TestDataError

  object SourceTestDataGenerationError {
    final case class SourceCompilationError(nodeId: String, errors: List[ProcessCompilationError])
        extends SourceTestDataGenerationError
    final case class UnsupportedSourceError(nodeId: String) extends SourceTestDataGenerationError
    final case object NoDataGenerated                       extends SourceTestDataGenerationError
  }

  sealed trait ScenarioTestDataGenerationError extends TestDataError

  object ScenarioTestDataGenerationError {
    final case object NoDataGenerated                 extends ScenarioTestDataGenerationError
    final case object NoSourcesWithTestDataGeneration extends ScenarioTestDataGenerationError
  }

  sealed trait TestDataPreparationError extends TestDataError

  object TestDataPreparationError {
    final case class MissingSource(sourceId: String, recordIndex: Int) extends TestDataPreparationError
    final case class MultipleSourcesRequired(recordIndex: Int)         extends TestDataPreparationError
  }

}
