package pl.touk.nussknacker.engine.lite

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.sample.{SampleInput, SourceFailure}
import pl.touk.nussknacker.engine.spel.SpelExtension._

//TODO: test for test-from-file
class StateEngineTest extends AnyFunSuite with Matchers with OptionValues {

  test("run scenario with sum aggregation") {
    val results = sample.run(
      sampleScenarioWithState,
      ScenarioInputBatch(List(0, 1, 2, 3).zipWithIndex.map { case (value, idx) =>
        (SourceId("start"), SampleInput(idx.toString, value))
      }),
      Map("test" -> 10)
    )

    // we start with 10, add 2 * each input, but omit 1 as enricher fails on that value
    results.value.map(er => (er.context.id, er.result)) shouldBe List(
      "0" -> "0:10.0",
      "2" -> "2:14.0",
      "3" -> "3:20.0"
    )
    results.written.map(_.context.id) shouldBe List("1")
  }

  test("run scenario failing on source") {
    val results = sample.run(
      sampleScenarioWithFailingSource,
      ScenarioInputBatch(List(0, 1, 2, 3).zipWithIndex.map { case (value, idx) =>
        (SourceId("start"), SampleInput(idx.toString, value))
      }),
      Map("test" -> 10)
    )

    // we start with 10, add 2 * each input, but omit 1 as enricher fails on that value
    results.value.map(er => (er.context.id, er.result)) shouldBe List(
      "0" -> 0,
      "2" -> 2,
      "3" -> 3
    )
    val error = results.written.headOption.value
    error.context.id shouldEqual "1"
    error.throwable shouldEqual SourceFailure
  }

  private lazy val sampleScenarioWithState: CanonicalProcess = ScenarioBuilder
    .streamingLite("next")
    .source("start", "start")
    .buildSimpleVariable("v1", "v1", "2 * #input".spel)
    .enricher("failOnNumber1", "out1", "failOnNumber1", "value" -> "#input".spel)
    .customNode("sum", "sum", "sum", "name" -> "'test'".spel, "value" -> "#v1".spel)
    .emptySink("end", "end", "value" -> "#input + ':' + #sum".spel)

  private lazy val sampleScenarioWithFailingSource: CanonicalProcess = ScenarioBuilder
    .streamingLite("next")
    .source("start", "failOnNumber1Source")
    .emptySink("end", "end", "value" -> "#input".spel)

}
