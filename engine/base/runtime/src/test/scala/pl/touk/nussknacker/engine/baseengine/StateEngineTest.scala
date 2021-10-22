package pl.touk.nussknacker.engine.baseengine

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.baseengine.api.commonTypes.DataBatch
import pl.touk.nussknacker.engine.baseengine.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.spel.Implicits._

//TODO: test for test-from-file
class StateEngineTest extends FunSuite with Matchers  {

  test("run scenario with sum aggregation") {

    val results = sample.run(sampleScenarioWithState, ScenarioInputBatch(List(0, 1, 2, 3).zipWithIndex.map { case (value, idx) =>
      (SourceId("start"), Context(idx.toString, Map("input" -> value), None))
    }), Map("test" -> 10))

    //we start with 10, add 2 * each input, but omit 1 as enricher fails on that value
    results.value.map(er => (er.context.id, er.result)) shouldBe List(
      "0" -> "0:10.0",
      "2" -> "2:14.0",
      "3" -> "3:20.0"
    )
    results.written.map(_.context.id) shouldBe List("1")
  }

  private def sampleScenarioWithState: EspProcess = EspProcessBuilder
    .id("next")
    .exceptionHandler()
    .source("start", "start")
    .buildSimpleVariable("v1", "v1", "2 * #input")
    .enricher("failOnNumber1", "out1", "failOnNumber1", "value" -> "#input")
    .customNode("sum", "sum", "sum", "name" -> "'test'", "value" -> "#v1")
    .emptySink("end", "end", "value" -> "#input + ':' + #sum")

}

