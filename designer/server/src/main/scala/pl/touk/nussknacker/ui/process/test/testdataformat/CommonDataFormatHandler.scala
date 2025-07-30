package pl.touk.nussknacker.ui.process.test.testdataformat

import cats.implicits.toBifunctorOps
import io.circe.parser
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.json.encoders.{StrictToJsonEncoder, ToJsonEncoder}
import pl.touk.nussknacker.engine.api.livedata.LiveDataProvider
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.process.test.{
  CommonFormatPreliminaryScenarioRecord,
  PreliminaryScenarioRecord,
  PreliminaryScenarioRecords,
  SerializedScenarioRecordsContent
}
import pl.touk.nussknacker.ui.process.test.testdataformat.TestDataFormatSerDe.DeserializationError
import shapeless.syntax.typeable.typeableOps

class CommonDataFormatHandler(modelData: ModelData) extends TestDataFormatHandler {

  override val serDe: TestDataFormatSerDe = CommonDataFormatSerDe

  private val toJsonEncoder = new StrictToJsonEncoder(modelData.modelClassLoader)

  override def canFetchLiveData(compiledSource: Source): Boolean = compiledSource.isInstanceOf[LiveDataProvider]

  override def canBeTested(compiledSource: Source): Boolean =
    true // For common format, every source can be used for scenario testing

  override def fetchLiveData(
      sourceId: NodeId,
      compiledSource: Source,
      maxNumberOfRecords: Int
  ): Either[TestDataFormatHandler.LiveDataFetchingNotSupportedError.type, List[PreliminaryScenarioRecord]] =
    compiledSource match {
      case liveDataProvider: LiveDataProvider =>
        val records = modelData.withModelClassloaderAsContextClassLoader {
          val sourceRecords = liveDataProvider.fetchLiveData(maxNumberOfRecords).records

          sourceRecords
            .map { record =>
              val variablesAsJson = record.variables.mapValuesNow(toJsonEncoder.encodeUnsafe)
              CommonFormatPreliminaryScenarioRecord(sourceId.id, variablesAsJson, record.timestamp)
            }
        }
        Right(records)
      case _ =>
        Left(TestDataFormatHandler.LiveDataFetchingNotSupportedError)
    }

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
  ): Either[DeserializationError, List[PreliminaryScenarioRecord]] = {
    parser
      .decode[List[PreliminaryScenarioRecord]](content.content)
      .leftMap(err => DeserializationError.RecordsParsingError(err.getMessage))
  }

}
