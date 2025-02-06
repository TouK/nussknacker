package pl.touk.nussknacker.engine.definition.test

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.test.TestInfoProvider.{TestDataGenerationError, TestDataPreparationError}
import pl.touk.nussknacker.engine.graph.node.SourceNodeData

trait TestInfoProvider {

  def getTestingCapabilities(processVersion: ProcessVersion, scenario: CanonicalProcess): TestingCapabilities

  def getTestParameters(processVersion: ProcessVersion, scenario: CanonicalProcess): Map[String, List[Parameter]]

  def generateTestData(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess,
      size: Int
  ): Either[TestDataGenerationError, PreliminaryScenarioTestData]

  def prepareTestData(
      preliminaryTestData: PreliminaryScenarioTestData,
      scenario: CanonicalProcess
  ): Either[TestDataPreparationError, ScenarioTestData]

  def generateTestDataForSource(
      metaData: MetaData,
      sourceNodeData: SourceNodeData,
      size: Int
  ): Either[TestDataGenerationError, PreliminaryScenarioTestData]

}

object TestInfoProvider {

  sealed trait TestDataError {
    def message: String
  }

  sealed trait TestDataGenerationError extends TestDataError

  object TestDataGenerationError {

    final case class SourceCompilationError(nodeId: String, errors: List[ProcessCompilationError])
        extends TestDataGenerationError {
      override def message: String = s"Source node can't be compiled. Problems: ${errors.mkString(", ")}"
    }

    final case class UnsupportedSourceError(nodeId: String) extends TestDataGenerationError {
      override def message: String = s"Source '$nodeId' doesn't support records preview"
    }

    final case object NoDataGenerated extends TestDataGenerationError {
      override def message: String = "No test data was generated"
    }

    final case object NoSourcesWithTestDataGeneration extends TestDataGenerationError {
      override def message: String = "Scenario doesn't have any valid source supporting test data generation"
    }

  }

  sealed trait TestDataPreparationError extends TestDataError

  object TestDataPreparationError {

    final case class MissingSourceError(sourceId: String, recordIndex: Int) extends TestDataPreparationError {
      override def message: String = s"Record ${recordIndex + 1} - scenario does not have source id: '$sourceId'"
    }

    final case class MultipleSourcesError(recordIndex: Int) extends TestDataPreparationError {
      override def message: String =
        s"Record ${recordIndex + 1} - scenario has multiple sources but got record without source id"
    }

  }

}
