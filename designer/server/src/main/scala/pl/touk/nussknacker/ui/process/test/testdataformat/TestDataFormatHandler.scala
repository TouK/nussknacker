package pl.touk.nussknacker.ui.process.test.testdataformat

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.ui.api.TestDataFormat
import pl.touk.nussknacker.ui.process.test.{PreliminaryScenarioRecord, PreliminaryScenarioRecords}

trait TestDataFormatHandler {

  // FIXME abr: better error type
  def fetchLiveData(
      sourceId: NodeId,
      compiledSource: Source,
      maxNumberOfRecords: Int
  ): Either[TestDataFormatHandler.LiveDataFetchingNotSupportedError.type, List[PreliminaryScenarioRecord]]

}

object TestDataFormatHandler {

  def apply(testDataFormat: TestDataFormat.Value, modelData: ModelData): TestDataFormatHandler = testDataFormat match {
    case TestDataFormat.SourceSpecific => new SourceSpecificDataFormatHandler(modelData)
    case TestDataFormat.CommonFormat   => ???
  }

  case object LiveDataFetchingNotSupportedError

}
