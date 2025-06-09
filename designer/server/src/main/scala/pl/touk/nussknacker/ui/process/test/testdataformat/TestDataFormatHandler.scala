package pl.touk.nussknacker.ui.process.test.testdataformat

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.ui.api.TestDataFormat
import pl.touk.nussknacker.ui.process.test.{
  PreliminaryScenarioRecord,
  PreliminaryScenarioRecords,
  SerializedScenarioRecordsContent
}
import pl.touk.nussknacker.ui.process.test.testdataformat.TestDataFormatSerDe.DeserializationError

trait TestDataFormatHandler {

  val serDe: TestDataFormatSerDe

  def canFetchLiveData(compiledSource: Source): Boolean

  def canBeTested(compiledSource: Source): Boolean

  def fetchLiveData(
      sourceId: NodeId,
      compiledSource: Source,
      maxNumberOfRecords: Int
  ): Either[TestDataFormatHandler.LiveDataFetchingNotSupportedError.type, List[PreliminaryScenarioRecord]]

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

}

object TestDataFormatSerDe {

  def apply(testDataFormat: TestDataFormat.Value): TestDataFormatSerDe = testDataFormat match {
    case TestDataFormat.SourceSpecific => SourceSpecificDataFormatSerDe
    case TestDataFormat.CommonFormat   => CommonDataFormatSerDe
  }

  sealed trait DeserializationError

  object DeserializationError {

    final case class RecordParsingError(serializedTestRecord: String, recordIndex: Int) extends DeserializationError

    final case class RecordsParsingError(message: String) extends DeserializationError

  }

}
