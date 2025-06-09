package pl.touk.nussknacker.ui.process.test.testdataformat

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator}
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioRecord
import shapeless.syntax.typeable.typeableOps

class SourceSpecificDataFormatHandler(modelData: ModelData) extends TestDataFormatHandler {

  override def fetchLiveData(
      sourceId: NodeId,
      compiledSource: Source,
      maxNumberOfRecords: Int
  ): Either[TestDataFormatHandler.LiveDataFetchingNotSupportedError.type, List[PreliminaryScenarioRecord]] = {
    compiledSource
      .cast[TestDataGenerator]
      .map { testDataGenerator =>
        val sourceTestRecords = modelData.withModelClassloaderAsContextClassLoader {
          testDataGenerator.generateTestData(maxNumberOfRecords).testRecords
        }
        Right(
          sourceTestRecords
            .map(testRecord => PreliminaryScenarioRecord(sourceId.id, testRecord.json, testRecord.timestamp))
        )
      }
      .getOrElse(Left(TestDataFormatHandler.LiveDataFetchingNotSupportedError))
  }

}
