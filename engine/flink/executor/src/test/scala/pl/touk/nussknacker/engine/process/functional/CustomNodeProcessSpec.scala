package pl.touk.nussknacker.engine.process.functional

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.spel.SpelExtension._

import java.util.Date

class CustomNodeProcessSpec extends AnyFunSuite with Matchers with ProcessTestHelpers {

  test("be able to use maps and lists after custom nodes") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .buildSimpleVariable("map", "map", "{:}".spel)
      .buildSimpleVariable("list", "list", "{}".spel)
      .customNode("custom", "outRec", "stateCustom", "groupBy" -> "#input.id".spel, "stringVal" -> "''".spel)
      .buildSimpleVariable("mapToString", "mapToString", "#map.toString()".spel)
      .buildSimpleVariable("listToString", "listToString", "#list.toString()".spel)
      .emptySink("out", "monitor")

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    // without certain hack (see SpelHack & SpelMapHack) this throws exception.
    processInvoker.invokeWithSampleData(process, data)

  }

  test("be able to use maps, lists and output var after optional ending custom nodes") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .buildSimpleVariable("map", "map", "{:}".spel)
      .buildSimpleVariable("list", "list", "{}".spel)
      .buildSimpleVariable("str", "strVar", "'someStr'".spel)
      .customNode("custom", "outRec", "optionalEndingCustom", "param" -> "#strVar".spel)
      .buildSimpleVariable("mapToString", "mapToString", "#map.toString()".spel)
      .buildSimpleVariable("listToString", "listToString", "#list.toString()".spel)
      .buildSimpleVariable("outputVarToString", "outRecToString", "#outRec.toString()".spel)
      .emptySink("out", "monitor")

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    // without certain hack (see SpelHack & SpelMapHack) this throws exception.
    processInvoker.invokeWithSampleData(process, data)
  }

  test("fire alert when aggregate threshold exceeded") {

    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .customNode("custom", "outRec", "stateCustom", "groupBy" -> "#input.id".spel, "stringVal" -> "'terefere'".spel)
      .filter("delta", "#outRec.record.value1 > #outRec.previous + 5".spel)
      .processor("proc2", "logService", "all" -> "#outRec".spel)
      .emptySink("out", "monitor")

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0)),
      SimpleRecord("1", 5, "b", new Date(1000)),
      SimpleRecord("1", 12, "d", new Date(4000)),
      SimpleRecord("1", 14, "d", new Date(10000)),
      SimpleRecord("1", 20, "d", new Date(10000))
    )

    processInvoker.invokeWithSampleData(process, data)

    val mockData = ProcessTestHelpers.logServiceResultsHolder.results.map(_.asInstanceOf[SimpleRecordWithPreviousValue])
    mockData.map(_.record.value1) shouldBe List(12L, 20L)
    mockData.map(_.added) shouldBe List("terefere", "terefere")

  }

  test("fire alert when aggregate threshold exceeded #2") {

    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .customNode("custom", "outRec", "stateCustom", "groupBy" -> "#input.id".spel, "stringVal" -> "'terefere'".spel)
      .split(
        "split",
        GraphBuilder
          .filter("delta", "#outRec.record.value1 > #outRec.previous + 5".spel)
          .processor("proc2", "logService", "all" -> "#outRec".spel)
          .emptySink("out", "monitor"),
        GraphBuilder
          .emptySink("out2", "monitor")
      )

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0)),
      SimpleRecord("1", 5, "b", new Date(1000)),
      SimpleRecord("1", 12, "d", new Date(4000)),
      SimpleRecord("1", 14, "d", new Date(10000)),
      SimpleRecord("1", 20, "d", new Date(10000))
    )

    processInvoker.invokeWithSampleData(process, data)

    val mockData = ProcessTestHelpers.logServiceResultsHolder.results.map(_.asInstanceOf[SimpleRecordWithPreviousValue])
    mockData.map(_.record.value1) shouldBe List(12L, 20L)
    mockData.map(_.added) shouldBe List("terefere", "terefere")

  }

  test("let use current context within custom node even when it clears its context afterwards") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .customNodeNoOutput("id1", "customContextClear", "value" -> "#input.id".spel)
      .processor("proc2", "logService", "all" -> "'42'".spel)
      .emptySink("out", "monitor")

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results.size shouldBe 1
  }

  test("be able to split after custom node") {
    val additionalFilterBranch = GraphBuilder
      .filter("falseFilter", "#outRec.record.value1 > #outRec.previous + 1".spel)
      .customNode("custom2", "outRec2", "stateCustom", "groupBy" -> "#input.id".spel, "stringVal" -> "'terefere'".spel)
      .emptySink("outFalse", "monitor")

    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .customNode("custom", "outRec", "stateCustom", "groupBy" -> "#input.id".spel, "stringVal" -> "'terefere'".spel)
      .split(
        "split",
        GraphBuilder.processorEnd("proc3", "logService", "all" -> "'allRec-' + #outRec.record.value1".spel),
        // additionalFilterBranch added, to make this case more complicated
        GraphBuilder
          .filter("delta", "#outRec.record.value1 > #outRec.previous + 5".spel, additionalFilterBranch)
          .processor("proc2", "logService", "all" -> "#outRec.record.value1 + '-' + #outRec.added".spel)
          .emptySink("out", "monitor")
      )

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0)),
      SimpleRecord("1", 5, "b", new Date(1000)),
      SimpleRecord("1", 12, "d", new Date(4000)),
      SimpleRecord("1", 14, "d", new Date(10000)),
      SimpleRecord("1", 20, "d", new Date(10000))
    )

    processInvoker.invokeWithSampleData(process, data)

    val (allMocked, filteredMocked) =
      ProcessTestHelpers.logServiceResultsHolder.results.map(_.asInstanceOf[String]).partition(_.startsWith("allRec-"))
    allMocked shouldBe List("allRec-3", "allRec-5", "allRec-12", "allRec-14", "allRec-20")
    filteredMocked shouldBe List("12-terefere", "20-terefere")

  }

  test("be able to filter before split") {

    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .filter("dummy", "false".spel)
      .split("split", GraphBuilder.processorEnd("proc2", "logService", "all" -> "#input".spel))

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldBe Symbol("empty")

  }

  test("retain context after split") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .buildSimpleVariable("a", "tv", "'alamakota'".spel)
      .split(
        "split",
        GraphBuilder.processorEnd("proc3", "logService", "all" -> "'f1-' + #tv".spel),
        GraphBuilder.processorEnd("proc4", "logService", "all" -> "'f2-' + #tv".spel)
      )

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    val all = ProcessTestHelpers.logServiceResultsHolder.results.toSet
    all shouldBe Set("f1-alamakota", "f2-alamakota")

  }

  test("be able to pass former context") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .buildSimpleVariable("testVar", "beforeNode", "'testBeforeNode'".spel)
      .customNode("custom", "outRec", "stateCustom", "groupBy" -> "#input.id".spel, "stringVal" -> "'terefere'".spel)
      .processorEnd("proc2", "logService", "all" -> "#beforeNode".spel)

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))
    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldBe List("testBeforeNode")

  }

  test("process custom node without return properly") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .buildSimpleVariable("testVar", "beforeNode", "'testBeforeNode'".spel)
      .customNodeNoOutput("custom", "customFilter", "input" -> "#input.id".spel, "stringVal" -> "'terefere'".spel)
      .processorEnd("proc2", "logService", "all" -> "#input.id".spel)

    val data = List(SimpleRecord("terefere", 3, "a", new Date(0)), SimpleRecord("kuku", 3, "b", new Date(0)))

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldBe List("terefere")

  }

  test("should be able to use ContextTransformation API") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .buildSimpleVariable("testVar", "beforeNode", "'testBeforeNode'".spel)
      .customNodeNoOutput(
        "custom",
        "customFilterContextTransformation",
        "input"     -> "#input.id".spel,
        "stringVal" -> "'terefere'".spel
      )
      .processorEnd("proc2", "logService", "all" -> "#input.id".spel)

    val data = List(SimpleRecord("terefere", 3, "a", new Date(0)), SimpleRecord("kuku", 3, "b", new Date(0)))

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldBe List("terefere")
  }

  test("not allow input after custom node clearing context") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .customNodeNoOutput("id1", "customContextClear", "value" -> "'ala'".spel)
      .processorEnd("proc2", "logService", "all" -> "#input.id".spel)

    val data = List()

    val thrown = the[IllegalArgumentException] thrownBy processInvoker.invokeWithSampleData(process, data)

    thrown.getMessage should startWith(
      "Compilation errors: ExpressionParserCompilationError(Unresolved reference 'input',proc2,Some(ParameterName(all)),#input.id,None)"
    )
  }

  test("should validate types in custom node output variable") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .customNode("custom", "outRec", "stateCustom", "groupBy" -> "#input.id".spel, "stringVal" -> "'terefere'".spel)
      .filter("delta", "#outRec.record.value999 > #outRec.previous + 5".spel)
      .processor("proc2", "logService", "all" -> "#outRec".spel)
      .emptySink("out", "monitor")

    val thrown = the[IllegalArgumentException] thrownBy processInvoker.invokeWithSampleData(process, List.empty)

    thrown.getMessage shouldBe s"Compilation errors: ExpressionParserCompilationError(There is no property 'value999' in type: SimpleRecord,delta,Some(ParameterName($$expression)),#outRec.record.value999 > #outRec.previous + 5,None)"
  }

  test("should evaluate blank expression used in lazy parameter as a null") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .customNode("id1", "output", "transformWithNullable", "param" -> "".spel)
      .processor("proc2", "logService", "all" -> "#output".spel)
      .emptySink("out", "monitor")

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldBe List(null)
  }

  test("be able to end process with optional ending custom node") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .buildSimpleVariable("map", "map", "{:}".spel)
      .buildSimpleVariable("list", "list", "{}".spel)
      .endingCustomNode("custom", None, "optionalEndingCustom", "param" -> "#input.id".spel)

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    // without certain hack (see SpelHack & SpelMapHack) this throws exception.
    processInvoker.invokeWithSampleData(process, data)
    ProcessTestHelpers.optionalEndingCustomResultsHolder.results shouldBe List("1")
  }

  test("listeners should count only incoming events to nodes") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .buildSimpleVariable("testVar", "beforeNode", "'testBeforeNode'".spel)
      .customNodeNoOutput("custom", "customFilter", "input" -> "#input.id".spel, "stringVal" -> "'terefere'".spel)
      .split(
        "split",
        GraphBuilder.emptySink("out", "monitor"),
        GraphBuilder.endingCustomNode("custom-ending", None, "optionalEndingCustom", "param" -> "'param'".spel)
      )
    val data = List(SimpleRecord("terefere", 3, "a", new Date(0)), SimpleRecord("kuku", 3, "b", new Date(0)))

    CountingNodesListener.listen {
      processInvoker.invokeWithSampleData(process, data)
    } shouldBe List("id", "testVar", "custom", "split", "out", "custom-ending", "id", "testVar", "custom")
  }

}
