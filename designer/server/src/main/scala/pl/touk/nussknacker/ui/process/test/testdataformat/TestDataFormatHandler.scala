package pl.touk.nussknacker.ui.process.test.testdataformat

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.ui.api.TestDataFormat
import pl.touk.nussknacker.ui.process.test.{
  PreliminaryScenarioRecord,
  PreliminaryScenarioRecords,
  SerializedScenarioRecordsContent
}
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.ParametersDefinitionError
import pl.touk.nussknacker.ui.process.test.testdataformat.TestDataFormatHandler.ExpressionsToTestDataConversionError
import pl.touk.nussknacker.ui.process.test.testdataformat.TestDataFormatSerDe.DeserializationError

trait TestDataFormatHandler {

  val serDe: TestDataFormatSerDe

  def canFetchLiveData(compiledSource: Source): Boolean

  def canBeTested(compiledSource: Source): Boolean

  def canTestWithForm(compiledSource: Source): Boolean

  def fetchLiveData(
      sourceId: NodeId,
      compiledSource: Source,
      maxNumberOfRecords: Int
  ): Either[TestDataFormatHandler.LiveDataFetchingNotSupportedError.type, List[PreliminaryScenarioRecord]]

  def getTestParametersDefinition(
      sourceId: NodeId,
      sourceName: NodeName,
      sourceCompilationResult: NodeCompilationResult[Source],
  ): Either[ParametersDefinitionError, List[Parameter]]

  def convertToTestData(
      sourceId: String,
      parameterExpressions: Map[ParameterName, Expression]
  ): Either[ExpressionsToTestDataConversionError, ScenarioTestData]

}

trait TestDataFormatSerDe {

  def serializeRecords(scenarioRecords: PreliminaryScenarioRecords): SerializedScenarioRecordsContent

  def deserializeRecords(
      content: SerializedScenarioRecordsContent
  ): Either[DeserializationError, List[PreliminaryScenarioRecord]]

}

object TestDataFormatHandler extends LazyLogging {

  def apply(testDataFormat: TestDataFormat.Value, modelData: ModelData): TestDataFormatHandler = testDataFormat match {
    case TestDataFormat.SourceSpecific =>
      logger.debug("Scenario testing mechanism is configured with source-specific test data format")
      new SourceSpecificDataFormatHandler(modelData)
    case TestDataFormat.CommonFormat =>
      logger.debug("Scenario testing mechanism is configured with common test data format")
      new CommonDataFormatHandler(modelData)
  }

  case object LiveDataFetchingNotSupportedError

  sealed trait ExpressionsToTestDataConversionError

  case object MissingInputVariablesParameter extends ExpressionsToTestDataConversionError

  final case class IllegalInputVariablesExpressionLanguage(language: Language)
      extends ExpressionsToTestDataConversionError

  final case class InputVariablesExpressionJsonParseError(message: String) extends ExpressionsToTestDataConversionError

  final case class InputVariablesExpressionDecodingError(message: String) extends ExpressionsToTestDataConversionError

}

object TestDataFormatSerDe {

  sealed trait DeserializationError

  object DeserializationError {

    final case class RecordParsingError(serializedTestRecord: String, recordIndex: Int) extends DeserializationError

    final case class RecordsParsingError(message: String) extends DeserializationError

  }

}
