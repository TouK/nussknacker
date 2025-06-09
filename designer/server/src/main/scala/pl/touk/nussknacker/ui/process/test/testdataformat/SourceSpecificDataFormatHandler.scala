package pl.touk.nussknacker.ui.process.test.testdataformat

import cats.implicits.{toBifunctorOps, toTraverseOps}
import io.circe.parser
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.{Source, SourceTestSupport, TestDataGenerator}
import pl.touk.nussknacker.ui.process.test.{
  PreliminaryScenarioRecord,
  PreliminaryScenarioRecords,
  SerializedScenarioRecordsContent,
  SourceSpecificFormatPreliminaryScenarioRecord
}
import pl.touk.nussknacker.ui.process.test.testdataformat.TestDataFormatSerDe.DeserializationError
import shapeless.syntax.typeable.typeableOps

class SourceSpecificDataFormatHandler(modelData: ModelData) extends TestDataFormatHandler {

  override val serDe: TestDataFormatSerDe = SourceSpecificDataFormatSerDe

  override def canFetchLiveData(compiledSource: Source): Boolean = compiledSource.isInstanceOf[TestDataGenerator]

  override def canBeTested(compiledSource: Source): Boolean = compiledSource.isInstanceOf[SourceTestSupport[_]]

  override def fetchLiveData(
      sourceId: NodeId,
      compiledSource: Source,
      maxNumberOfRecords: Int
  ): Either[TestDataFormatHandler.LiveDataFetchingNotSupportedError.type, List[PreliminaryScenarioRecord]] = {
    compiledSource
      .cast[TestDataGenerator]
      .map { testDataGenerator =>
        val sourceRecords = modelData.withModelClassloaderAsContextClassLoader {
          testDataGenerator.generateTestData(maxNumberOfRecords).testRecords
        }
        Right(
          sourceRecords
            .map { record =>
              SourceSpecificFormatPreliminaryScenarioRecord(sourceId.id, record.json, record.timestamp)
            }
        )
      }
      .getOrElse(Left(TestDataFormatHandler.LiveDataFetchingNotSupportedError))
  }

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
  ): Either[DeserializationError, List[PreliminaryScenarioRecord]] = {
    val serializedScenarioRecords = content.content.linesIterator.toList
    serializedScenarioRecords.mapWithIndex { (rawTestRecord, recordIndex) =>
      val parsedRecord = parser.decode[PreliminaryScenarioRecord](rawTestRecord)
      parsedRecord.leftMap(_ => DeserializationError.RecordParsingError(rawTestRecord, recordIndex))
    }.sequence
  }

}
