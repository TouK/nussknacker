package pl.touk.nussknacker.engine.baseengine

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes.SourceId
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.spel.Implicits._

//TODO: test for test-from-file
class StateEngineTest extends FunSuite with Matchers  {

  test("run scenario with sum aggregation") {

    val results = sample.run(sampleScenarioWithState, List("ala", "bela", "cela").zipWithIndex.map { case (value, idx) =>
      (SourceId("start"), Context(idx.toString, Map("input" -> value), None))
    }, Map("test" -> 2))

    results.value.map(er => (er.context.id, er.result)) shouldBe List(
      "1" -> "bela:10.0",
      "2" -> "cela:18.0"
    )
    results.written.map(_.context.id) shouldBe List("0")
  }

  private def sampleScenarioWithState: EspProcess = EspProcessBuilder
    .id("next")
    .exceptionHandler()
    .source("start", "start")
    .buildSimpleVariable("v1", "v1", "#input + '-add'")
    .enricher("failOnAla", "out1", "failOnAla", "value" -> "#input")
    .customNode("sum", "sum", "sum", "name" -> "'test'", "value" -> "#v1.length")
    .emptySink("end", "end", "value" -> "#input + ':' + #sum")

}

