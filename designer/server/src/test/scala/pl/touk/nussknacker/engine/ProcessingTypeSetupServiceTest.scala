package pl.touk.nussknacker.engine

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.processingtypesetup.EngineSetupName
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class ProcessingTypeSetupServiceTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  test("should query processing type based on provided parameters") {
    val aCategory = "aCategory"
    val bCategory = "bCategory"

    val streamingType = "streamingType"
    val batchType     = "batchType"
    val bCategoryType = "bCategoryType"

    val service = new ProcessingTypeSetupService(
      Map(
        ProcessingTypeCategory(streamingType, aCategory) -> ProcessingTypeSetup(
          ProcessingMode.Streaming,
          EngineSetup(EngineSetupName("aSetup"), List.empty)
        ),
        ProcessingTypeCategory(batchType, aCategory) -> ProcessingTypeSetup(
          ProcessingMode.Batch,
          EngineSetup(EngineSetupName("aSetup"), List.empty)
        ),
        ProcessingTypeCategory(bCategoryType, bCategory) -> ProcessingTypeSetup(
          ProcessingMode.Streaming,
          EngineSetup(EngineSetupName("aSetup"), List.empty)
        )
      )
    )

    service.processingType(Some(ProcessingMode.Streaming), None, aCategory).validValue shouldEqual streamingType
    service.processingType(Some(ProcessingMode.Batch), None, aCategory).validValue shouldEqual batchType
    service.processingType(None, None, bCategory).validValue shouldEqual bCategoryType
  }

}
