package pl.touk.nussknacker.ui.process.test.testdataformat

import cats.data.NonEmptyList
import cats.implicits.toBifunctorOps
import io.circe
import io.circe.{parser, Json}
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.definition.{JsonParameterEditor, Parameter}
import pl.touk.nussknacker.engine.api.json.encoders.StrictToJsonEncoder
import pl.touk.nussknacker.engine.api.livedata.LiveDataProvider
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.test.{ScenarioTestCommonFormatJsonRecord, ScenarioTestData}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.process.test._
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.ParametersDefinitionError
import pl.touk.nussknacker.ui.process.test.testdataformat.CommonDataFormatHandler.InputVariablesParameterName
import pl.touk.nussknacker.ui.process.test.testdataformat.TestDataFormatHandler.{
  IllegalInputVariablesExpressionLanguage,
  InputVariablesExpressionDecodingError,
  InputVariablesExpressionJsonParseError,
  MissingInputVariablesParameter
}
import pl.touk.nussknacker.ui.process.test.testdataformat.TestDataFormatSerDe.DeserializationError

class CommonDataFormatHandler(modelData: ModelData) extends TestDataFormatHandler {

  import pl.touk.nussknacker.engine.testmode.NonMapBasedRecordTypesOps._

  override val serDe: TestDataFormatSerDe = CommonDataFormatSerDe

  private val toJsonEncoder = new StrictToJsonEncoder(modelData.modelClassLoader)

  override def canFetchLiveData(compiledSource: Source): Boolean = compiledSource.isInstanceOf[LiveDataProvider]

  override def canBeTested(compiledSource: Source): Boolean =
    true // For common format, every source can be used for scenario testing

  override def canTestWithForm(compiledSource: Source): Boolean =
    true // For common format, every source can be used for scenario testing using form

  override def fetchLiveData(
      sourceId: NodeId,
      compiledSource: Source,
      maxNumberOfRecords: Int
  ): Either[TestDataFormatHandler.LiveDataFetchingNotSupportedError.type, List[CommonFormatPreliminaryScenarioRecord]] =
    compiledSource match {
      case liveDataProvider: LiveDataProvider =>
        val records = modelData.withModelClassloaderAsContextClassLoader {
          val sourceRecords = liveDataProvider.fetchLiveData(maxNumberOfRecords).records

          sourceRecords
            .map { record =>
              val variablesAsJson = record.variables.mapValuesNow(toJsonEncoder.encodeUnsafe)
              CommonFormatPreliminaryScenarioRecord(sourceId, variablesAsJson, record.upstreamTimestamp)
            }
        }
        Right(records)
      case _ =>
        Left(TestDataFormatHandler.LiveDataFetchingNotSupportedError)
    }

  override def getTestParametersDefinition(
      sourceId: NodeId,
      sourceName: NodeName,
      sourceCompilationResult: NodeCompilationResult[Source]
  ): Either[ScenarioTestService.ParametersDefinitionError, List[Parameter]] = {
    for {
      validOutputValidationContext <- sourceCompilationResult.validationContext.toEither.leftMap(errors =>
        ParametersDefinitionError.SourcesCompilationError(NonEmptyList.one(sourceId -> errors))
      )
      validationContextWithMapBasedRecordTypes = validOutputValidationContext.mapTypes(_.toMapBasedRecordTypes)
      recordTypeBasedOnOutputValidationContext = Typed.record(validationContextWithMapBasedRecordTypes.localVariables)
      singleInputVariablesParameter = Parameter(InputVariablesParameterName, recordTypeBasedOnOutputValidationContext)
        .copy(editors = List(JsonParameterEditor))
    } yield List(singleInputVariablesParameter)
  }

  // Handling of test data as parameters is left for the backward compatibility reasons. Eventually,
  // we should always communicate through test data in the json format
  override def convertToTestData(
      sourceId: String,
      parameterExpressions: Map[ParameterName, Expression]
  ): Either[TestDataFormatHandler.ExpressionsToTestDataConversionError, ScenarioTestData] = {
    for {
      inputVariablesExpression <- parameterExpressions
        .get(InputVariablesParameterName)
        .toRight(MissingInputVariablesParameter)
      _ <- Either.cond(
        inputVariablesExpression.language == Language.Json,
        (),
        IllegalInputVariablesExpressionLanguage(inputVariablesExpression.language)
      )
      validJson <- circe.parser
        .parse(inputVariablesExpression.expression)
        .leftMap(_.message)
        .leftMap(InputVariablesExpressionJsonParseError)
      validVariablesMap <- validJson
        .as[Map[String, Json]]
        .leftMap(_.message)
        .leftMap(InputVariablesExpressionDecodingError)
      // Timestamp handling is supported only during testing using test data in the json format
      singleRecord = ScenarioTestCommonFormatJsonRecord(NodeId(sourceId), validVariablesMap, upstreamTimestamp = None)
    } yield ScenarioTestData(List(singleRecord))
  }

}

object CommonDataFormatHandler {
  val InputVariablesParameterName: ParameterName = ParameterName("Input variables")
}

object CommonDataFormatSerDe extends TestDataFormatSerDe {

  override def serializeRecords(scenarioRecords: PreliminaryScenarioRecords): SerializedScenarioRecordsContent = {
    val content = scenarioRecords.records
      .map(_.asJson.noSpaces)
      .toList
      .mkString("[", ",", "]")
    SerializedScenarioRecordsContent(content)
  }

  override def deserializeRecords(
      content: SerializedScenarioRecordsContent
  ): Either[DeserializationError, List[CommonFormatPreliminaryScenarioRecord]] = {
    parser
      .decode[List[CommonFormatPreliminaryScenarioRecord]](content.content)
      .leftMap(err => DeserializationError.RecordsParsingError(err.getMessage))
  }

}
