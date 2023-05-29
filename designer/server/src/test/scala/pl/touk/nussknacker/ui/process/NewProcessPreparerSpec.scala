package pl.touk.nussknacker.ui.process

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData

class NewProcessPreparerSpec extends AnyFlatSpec with Matchers {

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

  // TODO: test for correctly setting defaults for a type specific property
  // TODO: test for missing configuration for a type specific property
  // TODO: test for exception when there are different initial data for type specific and defaults for additional property
  // TODO: test for setting default from initial type specific data when additional property config hash None default
  // TODO: test for handling of fragments

}
