package pl.touk.nussknacker.ui.process

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData

class NewProcessPreparerSpec extends FlatSpec with Matchers {

  val processDefinition = ProcessTestData.processDefinition

  it should "create new empty process" in {
    val processingType = "testProcessingType"

    val preparer = new NewProcessPreparer(
      mapProcessingTypeDataProvider(processingType -> ProcessTestData.streamingTypeSpecificInitialData),
      mapProcessingTypeDataProvider(processingType -> Map.empty)
    )

    val emptyProcess = preparer.prepareEmptyProcess("processId1", processingType, isSubprocess = false)

    emptyProcess.metaData.id shouldBe "processId1"
    emptyProcess.nodes shouldBe List.empty
  }
}
