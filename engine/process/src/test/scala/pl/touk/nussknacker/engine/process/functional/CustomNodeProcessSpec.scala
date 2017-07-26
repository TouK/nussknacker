package pl.touk.nussknacker.engine.process.functional


import java.util.Date

import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.process.ProcessTestHelpers.{MockService, SimpleRecord, SimpleRecordWithPreviousValue, processInvoker}
import pl.touk.nussknacker.engine.spel

class CustomNodeProcessSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  it should "fire alert when aggregate threshold exceeded" in {

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .customNode("custom", "outRec", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'terefere'")
      .filter("delta", "#outRec.record.value1 > #outRec.previous + 5")
      .processor("proc2", "logService", "all" -> "#outRec")
      .sink("out", "monitor")

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0)),
      SimpleRecord("1", 5, "b", new Date(1000)),
      SimpleRecord("1", 12, "d", new Date(4000)),
      SimpleRecord("1", 14, "d", new Date(10000)),
      SimpleRecord("1", 20, "d", new Date(10000))

    )

    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    processInvoker.invoke(process, data, env)

    val mockData = MockService.data.map(_.asInstanceOf[SimpleRecordWithPreviousValue])
    mockData.map(_.record.value1) shouldBe List(12L, 20L)
    mockData.map(_.added) shouldBe List("terefere", "terefere")

  }

  it should "be able to split after custom node" in {
    val additionalFilterBranch = GraphBuilder.filter("falseFilter", "#outRec.record.value1 > #outRec.previous + 1")
      .customNode("custom2", "outRec2", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'terefere'")
      .sink("outFalse", "monitor")

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .customNode("custom", "outRec", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'terefere'")
      .split("split",
        GraphBuilder.processorEnd("proc3", "logService", "all" -> "'allRec-' + #outRec.record.value1"),
        //additionalFilterBranch added, to make this case more complicated
        GraphBuilder.filter("delta", "#outRec.record.value1 > #outRec.previous + 5", additionalFilterBranch)
          .processor("proc2", "logService", "all" -> "#outRec.record.value1 + '-' + #outRec.added").sink("out", "monitor")
      )

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0)),
      SimpleRecord("1", 5, "b", new Date(1000)),
      SimpleRecord("1", 12, "d", new Date(4000)),
      SimpleRecord("1", 14, "d", new Date(10000)),
      SimpleRecord("1", 20, "d", new Date(10000))

    )

    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    processInvoker.invoke(process, data, env)

    val (allMocked, filteredMocked) = MockService.data.map(_.asInstanceOf[String]).partition(_.startsWith("allRec-"))
    allMocked shouldBe List("allRec-3", "allRec-5", "allRec-12", "allRec-14", "allRec-20")
    filteredMocked shouldBe List("12-terefere", "20-terefere")

  }

  it should "be able to filter before split" in {

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

    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    processInvoker.invoke(process, data, env)

    MockService.data shouldBe 'empty

  }

  it should "retain context after split" in {
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

    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    processInvoker.invoke(process, data, env)

    val all = MockService.data.toSet
    all shouldBe Set("f1-alamakota", "f2-alamakota")


  }

  it should "be able to pass former context" in {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .buildSimpleVariable("testVar", "beforeNode", "'testBeforeNode'")
      .customNode("custom", "outRec", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'terefere'")
      .processorEnd("proc2", "logService", "all" -> "#beforeNode")

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    processInvoker.invoke(process, data, env)

    MockService.data shouldBe List("testBeforeNode")

  }

  it should "process custom node without return properly" in {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .buildSimpleVariable("testVar", "beforeNode", "'testBeforeNode'")
      .customNodeNoOutput("custom", "customFilter", "input" -> "#input.id", "stringVal" -> "'terefere'")
      .processorEnd("proc2", "logService", "all" -> "#input.id")

    val data = List(SimpleRecord("terefere", 3, "a", new Date(0)), SimpleRecord("kuku", 3, "b", new Date(0)))

    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    processInvoker.invoke(process, data, env)

    MockService.data shouldBe List("terefere")

  }

  it should "not allow input after custom node clearing context" in {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .customNodeNoOutput("id1", "customContextClear", "value" -> "'ala'")
      .processorEnd("proc2", "logService", "all" -> "#input.id")

    val data = List()

    val env = StreamExecutionEnvironment.createLocalEnvironment(1)

    val thrown = the [IllegalArgumentException] thrownBy processInvoker.invoke(process, data, env)

    thrown.getMessage shouldBe "Compilation errors: ExpressionParseError(Unresolved references input,proc2,Some(all),#input.id)"


  }

}
