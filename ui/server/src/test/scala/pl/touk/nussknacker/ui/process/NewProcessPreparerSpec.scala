package pl.touk.nussknacker.ui.process

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.graph.evaluatedparam
import pl.touk.nussknacker.engine.api.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.api.graph.expression.Expression
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
      mapProcessingTypeDataProvider(processingType -> (_ => StreamMetaData(None))),
      mapProcessingTypeDataProvider(processingType -> Map.empty))

    val emptyProcess = preparer.prepareEmptyProcess("processId1", processingType, isSubprocess = false)

    emptyProcess.metaData.id shouldBe "processId1"
    emptyProcess.nodes shouldBe List.empty
    emptyProcess.exceptionHandlerRef shouldBe ExceptionHandlerRef(List.empty)
  }

  it should "create new empty process with exception handler params present" in {
    val processingType = "testProcessingType"

    val preparer = new NewProcessPreparer(mapProcessingTypeDataProvider(processingType -> processDeffinitionWithExceptionHandler),
      mapProcessingTypeDataProvider(processingType -> (_ => StreamMetaData(None))),
      mapProcessingTypeDataProvider(processingType -> Map.empty))

    val emptyProcess = preparer.prepareEmptyProcess("processId1", processingType, isSubprocess = false)

    emptyProcess.metaData.id shouldBe "processId1"
    emptyProcess.nodes shouldBe List.empty
    emptyProcess.exceptionHandlerRef shouldBe ExceptionHandlerRef(List(
      evaluatedparam.Parameter("param1", Expression("spel", ""))
    ))
  }

}
