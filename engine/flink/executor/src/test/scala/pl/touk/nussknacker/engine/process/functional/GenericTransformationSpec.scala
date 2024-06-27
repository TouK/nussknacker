package pl.touk.nussknacker.engine.process.functional

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{NodePassingStateToImplementation, SimpleRecord}

import java.util.Date

class GenericTransformationSpec extends AnyFunSuite with Matchers with ProcessTestHelpers {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  test("be able to generic transformation") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .customNode(
        "genericParametersNode",
        "outRec",
        "genericParametersNode",
        "par1"     -> "'val1,val2'".spel,
        "lazyPar1" -> "#input != null".spel,
        "val1"     -> "'aa'".spel,
        "val2"     -> "11".spel
      )
      .processorEnd("proc2", "logService", "all" -> "#outRec".spel)

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldBe List(TypedMap(Map("val1" -> "aa", "val2" -> 11)))
  }

  test("be able to use final state in generic transformation's implementation") {
    val processWithoutVariableDeclaration = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .customNode("generic-node", "result", "nodePassingStateToImplementation")
      .processorEnd("proc2", "logService", "all" -> "#result".spel)

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    processInvoker.invokeWithSampleData(processWithoutVariableDeclaration, data)
    ProcessTestHelpers.logServiceResultsHolder.results shouldBe List(false)

    ProcessTestHelpers.logServiceResultsHolder.clear()
    val processWithVariableDeclaration = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .buildSimpleVariable(
        "build-var",
        NodePassingStateToImplementation.VariableThatshouldBeDefinedBeforeNodeName,
        "null".spel
      )
      .customNode("generic-node", "result", "nodePassingStateToImplementation")
      .processorEnd("proc2", "logService", "all" -> "#result".spel)

    processInvoker.invokeWithSampleData(processWithVariableDeclaration, data)
    ProcessTestHelpers.logServiceResultsHolder.results shouldBe List(true)
  }

  test("be able to generic source and sink") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "genericParametersSource", "type" -> "'type2'".spel, "version" -> "3".spel)
      .emptySink(
        "proc2",
        "genericParametersSink",
        "value"   -> "#input".spel,
        "type"    -> "'type1'".spel,
        "version" -> "2".spel
      )

    processInvoker.invokeWithSampleData(process, Nil)

    ProcessTestHelpers.genericParameterSinkResultsHolder.results shouldBe List(
      s"type2-3+type1-2+componentUseCase:${ComponentUseCase.EngineRuntime}"
    )
  }

  test("be able to generic source with multiple variables on start (with multipart compilation)") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("procSource", "genericSourceWithCustomVariables", "elements" -> "{'test'}".spel)
      .filter("filter-uses-custom-variable-id1", "#additionalOne != null".spel)
      .filter("filter-uses-custom-variable-id2", "#additionalTwo != null".spel)
      .customNode("dummy-generic-node", "result", "nodePassingStateToImplementation")
      .buildSimpleVariable("dummy-variable", "varName1", "#result ? 'prefix' : 'prefix'".spel)
      .buildSimpleVariable(
        "variable-uses-custom-variable-id",
        "varName2",
        "#input + '|' + #additionalOne + '|' + #additionalTwo".spel
      )
      .emptySink(
        "proc2",
        "genericParametersSink",
        "value"   -> "#varName2".spel,
        "type"    -> "'type1'".spel,
        "version" -> "2".spel
      )

    processInvoker.invokeWithSampleData(process, Nil)

    ProcessTestHelpers.genericParameterSinkResultsHolder.results shouldBe List(
      s"test|transformed:test|4+type1-2+componentUseCase:${ComponentUseCase.EngineRuntime}"
    )
  }

}
