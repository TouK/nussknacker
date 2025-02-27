package pl.touk.nussknacker.engine.process.functional

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.process.helpers.{ProcessTestHelpers, SinkAccessingNodeContext}
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SimpleRecord

import java.util.Date

class FlinkSinkSpec extends AnyFunSuite with Matchers with ProcessTestHelpers {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  test("be able to use FlinkCustomNodeContext internally in order to build sink using this data") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .buildSimpleVariable("map", "map", "{:}".spel)
      .buildSimpleVariable("list", "list", "{}".spel)
      .customNode("custom", "outRec", "stateCustom", "groupBy" -> "#input.id".spel, "stringVal" -> "''".spel)
      .buildSimpleVariable("mapToString", "mapToString", "#map.toString()".spel)
      .buildSimpleVariable("listToString", "listToString", "#list.toString()".spel)
      .emptySink("out", "sinkAccessingNodeContext")

    val data = List(SimpleRecord("1", 3, "a", new Date(0)))

    // without certain hack (see SpelHack & SpelMapHack) this throws exception.
    processInvoker.invokeWithSampleData(process, data)

    SinkAccessingNodeContext.nodeId shouldBe "out"
  }

}
