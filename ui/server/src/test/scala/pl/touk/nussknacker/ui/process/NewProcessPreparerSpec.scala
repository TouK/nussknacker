package pl.touk.nussknacker.ui.process

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ClazzRef
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.ui.api.ProcessTestData
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType

class NewProcessPreparerSpec extends FlatSpec with Matchers {

  val processDeffinition = ProcessTestData.processDefinition
  val processDeffinitionWithExceptionHandler = processDeffinition
    .withExceptionHandlerFactory(
      pl.touk.nussknacker.engine.definition.DefinitionExtractor.Parameter("param1", ClazzRef(classOf[String]))
    )

  it should "create new empty process" in {
    val preparer = new NewProcessPreparer(Map(ProcessingType.Streaming -> processDeffinition))

    val emptyProcess = preparer.prepareEmptyProcess("processId1", ProcessingType.Streaming, isSubprocess = false)

    emptyProcess.metaData.id shouldBe "processId1"
    emptyProcess.nodes shouldBe List.empty
    emptyProcess.exceptionHandlerRef shouldBe ExceptionHandlerRef(List.empty)
  }

  it should "create new empty process with exception handler params present" in {
    val preparer = new NewProcessPreparer(Map(ProcessingType.Streaming -> processDeffinitionWithExceptionHandler))

    val emptyProcess = preparer.prepareEmptyProcess("processId1", ProcessingType.Streaming, isSubprocess = false)

    emptyProcess.metaData.id shouldBe "processId1"
    emptyProcess.nodes shouldBe List.empty
    emptyProcess.exceptionHandlerRef shouldBe ExceptionHandlerRef(List(
      pl.touk.nussknacker.engine.graph.param.Parameter("param1", "")
    ))
  }

}
