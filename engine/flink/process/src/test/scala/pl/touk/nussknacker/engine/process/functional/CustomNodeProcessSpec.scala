package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers.processInvoker
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.spel

class CustomNodeProcessSpec extends FunSuite with Matchers {

  import spel.Implicits._

  test("be able to use maps and lists after custom nodes") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .buildSimpleVariable("map", "map", "{:}")
      .buildSimpleVariable("list", "list", "{}")
      .customNode("custom", "outRec", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "''")
      .buildSimpleVariable("mapToString", "mapToString", "#map.toString()")
      .buildSimpleVariable("listToString", "listToString", "#list.toString()")
      .emptySink("out", "monitor")

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    //without certain hack (see SpelHack & SpelMapHack) this throws exception.
    processInvoker.invokeWithSampleData(process, data)

  }

  test("fire alert when aggregate threshold exceeded") {

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .customNode("custom", "outRec", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'terefere'")
      .filter("delta", "#outRec.record.value1 > #outRec.previous + 5")
      .processor("proc2", "logService", "all" -> "#outRec")
      .emptySink("out", "monitor")

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0)),
      SimpleRecord("1", 5, "b", new Date(1000)),
      SimpleRecord("1", 12, "d", new Date(4000)),
      SimpleRecord("1", 14, "d", new Date(10000)),
      SimpleRecord("1", 20, "d", new Date(10000))

    )

    processInvoker.invokeWithSampleData(process, data)

    val mockData = MockService.data.map(_.asInstanceOf[SimpleRecordWithPreviousValue])
    mockData.map(_.record.value1) shouldBe List(12L, 20L)
    mockData.map(_.added) shouldBe List("terefere", "terefere")

  }

  test("fire alert when aggregate threshold exceeded #2") {

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .customNode("custom", "outRec", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'terefere'")
      .split("split",
        GraphBuilder
          .filter("delta", "#outRec.record.value1 > #outRec.previous + 5")
          .processor("proc2", "logService", "all" -> "#outRec")
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

    val mockData = MockService.data.map(_.asInstanceOf[SimpleRecordWithPreviousValue])
    mockData.map(_.record.value1) shouldBe List(12L, 20L)
    mockData.map(_.added) shouldBe List("terefere", "terefere")

  }

  test("let use current context within custom node even when it clears its context afterwards") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .customNodeNoOutput("id1", "customContextClear", "value" -> "#input.id")
      .processor("proc2", "logService", "all" -> "'42'")
      .emptySink("out", "monitor")

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    processInvoker.invokeWithSampleData(process, data)

    MockService.data.size shouldBe 1
  }

  test("be able to split after custom node") {
    val additionalFilterBranch = GraphBuilder.filter("falseFilter", "#outRec.record.value1 > #outRec.previous + 1")
      .customNode("custom2", "outRec2", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'terefere'")
      .emptySink("outFalse", "monitor")

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .customNode("custom", "outRec", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'terefere'")
      .split("split",
        GraphBuilder.processorEnd("proc3", "logService", "all" -> "'allRec-' + #outRec.record.value1"),
        //additionalFilterBranch added, to make this case more complicated
        GraphBuilder.filter("delta", "#outRec.record.value1 > #outRec.previous + 5", additionalFilterBranch)
          .processor("proc2", "logService", "all" -> "#outRec.record.value1 + '-' + #outRec.added").emptySink("out", "monitor")
      )

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0)),
      SimpleRecord("1", 5, "b", new Date(1000)),
      SimpleRecord("1", 12, "d", new Date(4000)),
      SimpleRecord("1", 14, "d", new Date(10000)),
      SimpleRecord("1", 20, "d", new Date(10000))

    )

    processInvoker.invokeWithSampleData(process, data)

    val (allMocked, filteredMocked) = MockService.data.map(_.asInstanceOf[String]).partition(_.startsWith("allRec-"))
    allMocked shouldBe List("allRec-3", "allRec-5", "allRec-12", "allRec-14", "allRec-20")
    filteredMocked shouldBe List("12-terefere", "20-terefere")

  }

  test("be able to filter before split") {

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .filter("dummy", "false")
      .split("split",
        GraphBuilder.processorEnd("proc2", "logService", "all" -> "#input")
      )

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldBe 'empty

  }

  test("retain context after split") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .buildSimpleVariable("a", "tv", "'alamakota'")
      .split("split",
        GraphBuilder.processorEnd("proc3", "logService", "all" -> "'f1-' + #tv"),
        GraphBuilder.processorEnd("proc4", "logService", "all" -> "'f2-' + #tv")
      )

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    val all = MockService.data.toSet
    all shouldBe Set("f1-alamakota", "f2-alamakota")


  }

  test("be able to pass former context") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .buildSimpleVariable("testVar", "beforeNode", "'testBeforeNode'")
      .customNode("custom", "outRec", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'terefere'")
      .processorEnd("proc2", "logService", "all" -> "#beforeNode")

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))
    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldBe List("testBeforeNode")

  }

  test("process custom node without return properly") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .buildSimpleVariable("testVar", "beforeNode", "'testBeforeNode'")
      .customNodeNoOutput("custom", "customFilter", "input" -> "#input.id", "stringVal" -> "'terefere'")
      .processorEnd("proc2", "logService", "all" -> "#input.id")

    val data = List(SimpleRecord("terefere", 3, "a", new Date(0)), SimpleRecord("kuku", 3, "b", new Date(0)))

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldBe List("terefere")

  }

  test("should be able to use ContextTransformation API") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .buildSimpleVariable("testVar", "beforeNode", "'testBeforeNode'")
      .customNodeNoOutput("custom", "customFilterContextTransformation", "input" -> "#input.id", "stringVal" -> "'terefere'")
      .processorEnd("proc2", "logService", "all" -> "#input.id")

    val data = List(SimpleRecord("terefere", 3, "a", new Date(0)), SimpleRecord("kuku", 3, "b", new Date(0)))

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldBe List("terefere")
  }

  test("not allow input after custom node clearing context") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .customNodeNoOutput("id1", "customContextClear", "value" -> "'ala'")
      .processorEnd("proc2", "logService", "all" -> "#input.id")

    val data = List()

    val thrown = the [IllegalArgumentException] thrownBy processInvoker.invokeWithSampleData(process, data)

    thrown.getMessage shouldBe "Compilation errors: ExpressionParseError(Unresolved reference 'input',proc2,Some(all),#input.id)"
  }

  test("should validate types in custom node output variable") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .customNode("custom", "outRec", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'terefere'")
      .filter("delta", "#outRec.record.value999 > #outRec.previous + 5")
      .processor("proc2", "logService", "all" -> "#outRec")
      .emptySink("out", "monitor")

    val thrown = the [IllegalArgumentException] thrownBy processInvoker.invokeWithSampleData(process, List.empty)

    thrown.getMessage shouldBe s"Compilation errors: ExpressionParseError(There is no property 'value999' in type: ${classOf[SimpleRecord].getName},delta,Some($$expression),#outRec.record.value999 > #outRec.previous + 5)"
  }

}
