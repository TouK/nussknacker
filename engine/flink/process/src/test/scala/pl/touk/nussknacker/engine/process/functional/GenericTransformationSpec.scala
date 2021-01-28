package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{MockService, NodePassingStateToImplementation, SimpleRecord, SinkForStrings}
import pl.touk.nussknacker.engine.spel

class GenericTransformationSpec extends FunSuite with Matchers with ProcessTestHelpers {

  import spel.Implicits._


  test("be able to generic transformation") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")

      .customNode("genericParametersNode", "outRec", "genericParametersNode",
        "par1" -> "'val1,val2'",
           "lazyPar1" -> "#input != null",
           "val1" -> "'aa'",
           "val2" -> "11")
      .processorEnd("proc2", "logService", "all" -> "#outRec")

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldBe List(TypedMap(Map("val1" -> "aa", "val2" -> 11)))
  }

  test("be able to use final state in generic transformation's implementation") {
    val processWithoutVariableDeclaration = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .customNode("generic-node", "result", "nodePassingStateToImplementation")
      .processorEnd("proc2", "logService", "all" -> "#result")

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    processInvoker.invokeWithSampleData(processWithoutVariableDeclaration, data)
    MockService.data shouldBe List(false)

    MockService.clear()
    val processWithVariableDeclaration = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .buildSimpleVariable("build-var", NodePassingStateToImplementation.VariableThatShouldBeDefinedBeforeNodeName, "")
      .customNode("generic-node", "result", "nodePassingStateToImplementation")
      .processorEnd("proc2", "logService", "all" -> "#result")

    processInvoker.invokeWithSampleData(processWithVariableDeclaration, data)
    MockService.data shouldBe List(true)
  }

  test("be able to generic source and sink") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "genericParametersSource", "type" -> "'type2'", "version" -> "3")
      .emptySink("proc2", "genericParametersSink", "value" -> "#input", "type" -> "'type1'", "version" -> "2")


    processInvoker.invokeWithSampleData(process, Nil)

    SinkForStrings.data shouldBe List("type2-3+type1-2")
  }

  test("be able to generic source with multiple variables on start") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("procSource", "genericParametersSourceWithAdditionalVariable")
      .filter("filter-id", "#additionalVariableOnStart != null")
      .customNode("generic-node", "result", "nodePassingStateToImplementation")
      .buildSimpleVariable("variable-id", "varName", "#result ? 'prefix' : 'prefix'")
      .emptySink("proc2", "genericParametersSink", "value" -> "#varName + ' ' + #additionalVariableOnStart", "type" -> "'type1'", "version" -> "2")

    processInvoker.invokeWithSampleData(process, Nil)

    SinkForStrings.data shouldBe List("prefix some additional value (emitted element)+type1-2")
  }
}
