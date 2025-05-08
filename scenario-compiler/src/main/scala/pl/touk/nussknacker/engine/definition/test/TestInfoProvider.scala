package pl.touk.nussknacker.engine.definition.test

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.test.TestInfoProvider._
import pl.touk.nussknacker.engine.graph.node.SourceNodeData

trait TestInfoProvider {

  def getTestingCapabilities(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): Either[TestingCapabilitiesError, TestingCapabilities]

  def getTestParameters(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): Either[ParametersDefinitionError, Map[String, List[Parameter]]]

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
    final case class SourceCompilationError(nodeId: NodeId, errors: NonEmptyList[ProcessCompilationError])
        extends SourceTestDataGenerationError
    final case class UnsupportedSourceError(nodeId: NodeId) extends SourceTestDataGenerationError
    final case object NoDataGenerated                       extends SourceTestDataGenerationError
  }

  sealed trait ScenarioTestDataGenerationError extends TestDataError

  object ScenarioTestDataGenerationError {

    final case class ScenarioGraphValidationError(
        nodesWithErrors: NonEmptyList[(NodeId, NonEmptyList[ProcessCompilationError])]
    ) extends ScenarioTestDataGenerationError

    final case object NoDataGenerated                 extends ScenarioTestDataGenerationError
    final case object NoSourcesWithTestDataGeneration extends ScenarioTestDataGenerationError
  }

  sealed trait TestDataPreparationError extends TestDataError

  object TestDataPreparationError {
    final case class MissingSource(sourceId: NodeId, recordIndex: Int) extends TestDataPreparationError
    final case class MultipleSourcesRequired(recordIndex: Int)         extends TestDataPreparationError
  }

  sealed trait TestingCapabilitiesError

  object TestingCapabilitiesError {
    case object NoSourcesError         extends TestingCapabilitiesError
    case object SourceCompilationError extends TestingCapabilitiesError
  }

  sealed trait ParametersDefinitionError {
    def message: String
  }

  object ParametersDefinitionError {
    final case class NotSupportedBySource(message: String)  extends ParametersDefinitionError
    final case class SourceValidationError(message: String) extends ParametersDefinitionError
  }

}
