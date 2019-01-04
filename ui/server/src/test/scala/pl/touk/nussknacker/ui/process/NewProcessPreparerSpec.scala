package pl.touk.nussknacker.ui.process

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.ui.api.ProcessTestData
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder.ObjectProcessDefinition

class NewProcessPreparerSpec extends FlatSpec with Matchers {

  val processDeffinition = ProcessTestData.processDefinition
  val processDeffinitionWithExceptionHandler = processDeffinition
    .withExceptionHandlerFactory(
      pl.touk.nussknacker.engine.api.definition.Parameter("param1", ClazzRef(classOf[String]))
    )

  it should "create new empty process" in {
    val processingType = "testProcessingType"

    val preparer = new NewProcessPreparer(Map(processingType -> processDeffinition), Map(processingType -> (_ => StreamMetaData(None))), Map(processingType -> Map.empty))

    val emptyProcess = preparer.prepareEmptyProcess("processId1", processingType, isSubprocess = false)

    emptyProcess.metaData.id shouldBe "processId1"
    emptyProcess.nodes shouldBe List.empty
    emptyProcess.exceptionHandlerRef shouldBe ExceptionHandlerRef(List.empty)
  }

  it should "create new empty process with exception handler params present" in {
    val processingType = "testProcessingType"

    val preparer = new NewProcessPreparer(Map(processingType -> processDeffinitionWithExceptionHandler),
      Map(processingType -> (_ => StreamMetaData(None))),
      Map(processingType -> Map.empty))

    val emptyProcess = preparer.prepareEmptyProcess("processId1", processingType, isSubprocess = false)

    emptyProcess.metaData.id shouldBe "processId1"
    emptyProcess.nodes shouldBe List.empty
    emptyProcess.exceptionHandlerRef shouldBe ExceptionHandlerRef(List(
      pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter("param1", Expression("spel", ""))
    ))
  }

}
