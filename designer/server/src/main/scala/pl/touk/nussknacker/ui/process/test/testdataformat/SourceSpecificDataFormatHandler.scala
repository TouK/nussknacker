package pl.touk.nussknacker.ui.process.test.testdataformat

import cats.data.NonEmptyList
import cats.implicits.{toBifunctorOps, toTraverseOps}
import io.circe.parser
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Source, SourceTestSupport, TestDataGenerator, TestWithParametersSupport}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.ui.process.test._
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.ParametersDefinitionError
import pl.touk.nussknacker.ui.process.test.testdataformat.TestDataFormatSerDe.DeserializationError
import shapeless.syntax.typeable.typeableOps

class SourceSpecificDataFormatHandler(modelData: ModelData) extends TestDataFormatHandler {

  override val serDe: TestDataFormatSerDe = SourceSpecificDataFormatSerDe

  override def canFetchLiveData(compiledSource: Source): Boolean = compiledSource.isInstanceOf[TestDataGenerator]

  override def canBeTested(compiledSource: Source): Boolean = compiledSource.isInstanceOf[SourceTestSupport[_]]

  override def canTestWithForm(compiledSource: Source): Boolean =
    compiledSource.isInstanceOf[TestWithParametersSupport[_]]

  override def fetchLiveData(
      sourceId: NodeId,
      compiledSource: Source,
      maxNumberOfRecords: Int
  ): Either[TestDataFormatHandler.LiveDataFetchingNotSupportedError.type, List[
    SourceSpecificFormatPreliminaryScenarioRecord
  ]] = {
    compiledSource
      .cast[TestDataGenerator]
      .map { testDataGenerator =>
        val sourceRecords = modelData.withModelClassloaderAsContextClassLoader {
          testDataGenerator.generateTestData(maxNumberOfRecords).testRecords
        }
        Right(
          sourceRecords
            .map { record =>
              SourceSpecificFormatPreliminaryScenarioRecord(sourceId, record.json, record.timestamp)
            }
        )
      }
      .getOrElse(Left(TestDataFormatHandler.LiveDataFetchingNotSupportedError))
  }

  override def getTestParametersDefinition(
      sourceId: NodeId,
      sourceName: NodeName,
      sourceCompilationResult: NodeCompilationResult[Source]
  ): Either[ScenarioTestService.ParametersDefinitionError, List[Parameter]] = {
    for {
      compiledSource <- sourceCompilationResult.compiledObject.toEither.leftMap(errors =>
        ParametersDefinitionError.SourcesCompilationError(NonEmptyList.one(sourceId -> errors))
      )
      testParameters <- getTestParameters(sourceId, sourceName, compiledSource)
    } yield testParameters
  }

  private def getTestParameters(
      sourceId: NodeId,
      sourceName: NodeName,
      compiledSource: Source,
  ): Either[ParametersDefinitionError, List[Parameter]] = {
    compiledSource match {
      case s: TestWithParametersSupport[_] => Right(s.testParametersDefinition)
      case _ => Left(ParametersDefinitionError.TestingWithCustomInputNotSupportedError(sourceId, sourceName))
    }
  }

  override def convertToTestData(
      sourceId: String,
      parameterExpressions: Map[ParameterName, Expression]
  ): Either[TestDataFormatHandler.ExpressionsToTestDataConversionError, ScenarioTestData] = Right(
    ScenarioTestData(sourceId, parameterExpressions)
  )

}

object SourceSpecificDataFormatSerDe extends TestDataFormatSerDe {

  override def serializeRecords(scenarioRecords: PreliminaryScenarioRecords): SerializedScenarioRecordsContent = {
    val content = scenarioRecords.records
      .map(_.asJson.noSpaces)
      .toList
      .mkString("\n")
    SerializedScenarioRecordsContent(content)
  }

  override def deserializeRecords(
      content: SerializedScenarioRecordsContent
  ): Either[DeserializationError, List[SourceSpecificFormatPreliminaryScenarioRecord]] = {
    val serializedScenarioRecords = content.content.linesIterator.toList
    serializedScenarioRecords.mapWithIndex { (rawTestRecord, recordIndex) =>
      val parsedRecord = parser.decode[SourceSpecificFormatPreliminaryScenarioRecord](rawTestRecord)
      parsedRecord.leftMap(_ => DeserializationError.RecordParsingError(rawTestRecord, recordIndex))
    }.sequence
  }

}
