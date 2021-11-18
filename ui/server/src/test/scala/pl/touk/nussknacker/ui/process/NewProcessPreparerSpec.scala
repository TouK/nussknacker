package pl.touk.nussknacker.ui.process

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder.ObjectProcessDefinition
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData

class NewProcessPreparerSpec extends FlatSpec with Matchers {

  val processDeffinition = ProcessTestData.processDefinition
  val processDeffinitionWithExceptionHandler = processDeffinition
    .withExceptionHandlerFactory(
      pl.touk.nussknacker.engine.api.definition.Parameter[String]("param1")
    )

  it should "create new empty process" in {
    val processingType = "testProcessingType"

    val preparer = new NewProcessPreparer(mapProcessingTypeDataProvider(processingType -> processDeffinition),
      mapProcessingTypeDataProvider(processingType -> ProcessTestData.streamingTypeSpecificDataInitializer),
      mapProcessingTypeDataProvider(processingType -> Map.empty))

    val emptyProcess = preparer.prepareEmptyProcess("processId1", processingType, isSubprocess = false)

    emptyProcess.metaData.id shouldBe "processId1"
    emptyProcess.nodes shouldBe List.empty
  }

  it should "create new empty process with exception handler params present" in {
    val processingType = "testProcessingType"

    val preparer = new NewProcessPreparer(mapProcessingTypeDataProvider(processingType -> processDeffinitionWithExceptionHandler),
      mapProcessingTypeDataProvider(processingType -> ProcessTestData.streamingTypeSpecificDataInitializer),
      mapProcessingTypeDataProvider(processingType -> Map.empty))

    val emptyProcess = preparer.prepareEmptyProcess("processId1", processingType, isSubprocess = false)

    emptyProcess.metaData.id shouldBe "processId1"
    emptyProcess.nodes shouldBe List.empty
  }

}
