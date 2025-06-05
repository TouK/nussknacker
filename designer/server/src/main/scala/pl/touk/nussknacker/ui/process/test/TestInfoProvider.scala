package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.ui.process.test.TestInfoProvider._

trait TestInfoProvider {

  def getTestingCapabilities(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): Either[TestingCapabilitiesError, TestingCapabilities]

  def getTestParameters(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): Either[ParametersDefinitionError, Map[String, List[Parameter]]]

  def prepareTestData(
      preliminaryTestData: PreliminaryScenarioTestData,
      scenario: CanonicalProcess
  ): Either[TestDataPreparationError, ScenarioTestData]

  def fetchSourcesLiveData(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess,
      maxNumberOfSamples: Int
  ): Either[SourcesLiveDataFetchingError, PreliminaryScenarioTestData]

  def fetchSourceLiveData(
      metaData: MetaData,
      sourceNodeData: SourceNodeData,
      maxNumberOfSamples: Int
  ): Either[SourceTestDataGenerationError, PreliminaryScenarioTestData]

}

object TestInfoProvider {

  sealed trait TestDataError

  sealed trait SourceTestDataGenerationError extends TestDataError

  object SourceTestDataGenerationError {
    final case class SourceCompilationError(nodeId: NodeId, errors: NonEmptyList[ProcessCompilationError])
        extends SourceTestDataGenerationError
    final case class UnsupportedSourceError(nodeId: NodeId) extends SourceTestDataGenerationError
    final case object NoLiveDataAvailable                   extends SourceTestDataGenerationError
  }

  sealed trait SourcesLiveDataFetchingError extends TestDataError

  object SourcesLiveDataFetchingError {

    final case class ScenarioGraphValidationError(
        nodesWithErrors: NonEmptyList[(NodeId, NonEmptyList[ProcessCompilationError])]
    ) extends SourcesLiveDataFetchingError

    final case object NoLiveDataAvailable                  extends SourcesLiveDataFetchingError
    final case object NoSourcesWithLiveDataFetchingSupport extends SourcesLiveDataFetchingError
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
