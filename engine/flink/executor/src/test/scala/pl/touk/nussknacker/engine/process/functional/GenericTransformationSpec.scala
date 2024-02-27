package pl.touk.nussknacker.engine.process.functional

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{NodePassingStateToRuntimeLogic, SimpleRecord}
import pl.touk.nussknacker.engine.spel

import java.util.Date

class GenericTransformationSpec extends AnyFunSuite with Matchers with ProcessTestHelpers {

  import spel.Implicits._

  test("be able to generic transformation") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .customNode(
        "genericParametersNode",
        "outRec",
        "genericParametersNode",
        "par1"     -> "'val1,val2'",
        "lazyPar1" -> "#input != null",
        "val1"     -> "'aa'",
        "val2"     -> "11"
      )
      .processorEnd("proc2", "logService", "all" -> "#outRec")

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldBe List(TypedMap(Map("val1" -> "aa", "val2" -> 11)))
  }

  test("be able to use final state in generic transformation's implementation") {
    val processWithoutVariableDeclaration = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .customNode("generic-node", "result", "nodePassingStateToImplementation")
      .processorEnd("proc2", "logService", "all" -> "#result")

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    processInvoker.invokeWithSampleData(processWithoutVariableDeclaration, data)
    ProcessTestHelpers.logServiceResultsHolder.results shouldBe List(false)

    ProcessTestHelpers.logServiceResultsHolder.clear()
    val processWithVariableDeclaration = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .buildSimpleVariable(
        "build-var",
        NodePassingStateToRuntimeLogic.VariableThatShouldBeDefinedBeforeNodeName,
        "null"
      )
      .customNode("generic-node", "result", "nodePassingStateToImplementation")
      .processorEnd("proc2", "logService", "all" -> "#result")

    processInvoker.invokeWithSampleData(processWithVariableDeclaration, data)
    ProcessTestHelpers.logServiceResultsHolder.results shouldBe List(true)
  }

  test("be able to generic source and sink") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "genericParametersSource", "type" -> "'type2'", "version" -> "3")
      .emptySink("proc2", "genericParametersSink", "value" -> "#input", "type" -> "'type1'", "version" -> "2")

    processInvoker.invokeWithSampleData(process, Nil)

    ProcessTestHelpers.genericParameterSinkResultsHolder.results shouldBe List(
      s"type2-3+type1-2+componentUseCase:${ComponentUseCase.EngineRuntime}"
    )
  }

  test("be able to generic source with multiple variables on start (with multipart compilation)") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("procSource", "genericSourceWithCustomVariables", "elements" -> "{'test'}")
      .filter("filter-uses-custom-variable-id1", "#additionalOne != null")
      .filter("filter-uses-custom-variable-id2", "#additionalTwo != null")
      .customNode("dummy-generic-node", "result", "nodePassingStateToImplementation")
      .buildSimpleVariable("dummy-variable", "varName1", "#result ? 'prefix' : 'prefix'")
      .buildSimpleVariable(
        "variable-uses-custom-variable-id",
        "varName2",
        "#input + '|' + #additionalOne + '|' + #additionalTwo"
      )
      .emptySink("proc2", "genericParametersSink", "value" -> "#varName2", "type" -> "'type1'", "version" -> "2")

    processInvoker.invokeWithSampleData(process, Nil)

    ProcessTestHelpers.genericParameterSinkResultsHolder.results shouldBe List(
      s"test|transformed:test|4+type1-2+componentUseCase:${ComponentUseCase.EngineRuntime}"
    )
  }

}
