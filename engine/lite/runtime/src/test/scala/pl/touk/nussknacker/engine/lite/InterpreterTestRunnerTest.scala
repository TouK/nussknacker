package pl.touk.nussknacker.engine.lite

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestRecord}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testmode.TestProcess.{ExpressionInvocationResult, ExternalInvocationResult, NodeResult, ResultContext}

class InterpreterTestRunnerTest extends AnyFunSuite with Matchers {

  test("should test single source scenario") {
    val scenario = ScenarioBuilder
      .streamingLite("scenario1")
      .source("start", "start")
      .enricher("failOnNumber1", "out1", "failOnNumber1", "value" -> "#input")
      .customNode("sum", "sum", "sum", "name" -> "'test'", "value" -> "#input")
      .emptySink("end", "end", "value" -> "#input + ':' + #sum")
    val scenarioTestData = ScenarioTestData(List(
      ScenarioTestRecord("start", Json.fromString("A|2")),
      ScenarioTestRecord("start", Json.fromString("B|1")),
      ScenarioTestRecord("start", Json.fromString("C|3")),
    ))

    val results = sample.test(scenario, scenarioTestData)

    results.nodeResults("start") shouldBe List(NodeResult(ResultContext("A", Map("input" -> 2))),
      NodeResult(ResultContext("B", Map("input" -> 1))), NodeResult(ResultContext("C", Map("input" -> 3))))
    results.nodeResults("sum") shouldBe List(NodeResult(ResultContext("A", Map("input" -> 2, "out1" -> 2))),
      NodeResult(ResultContext("C", Map("input" -> 3, "out1" -> 3))))
    results.nodeResults("end") shouldBe List(NodeResult(ResultContext("A", Map("input" -> 2, "out1" -> 2, "sum" -> 2))),
      NodeResult(ResultContext("C", Map("input" -> 3, "out1" -> 3, "sum" -> 5))))

    results.invocationResults("sum") shouldBe List(ExpressionInvocationResult("A", "value", 2), ExpressionInvocationResult("C", "value", 3))

    results.externalInvocationResults("end") shouldBe List(ExternalInvocationResult("A", "end", "2:2.0"), ExternalInvocationResult("C", "end","3:5.0"))
  }

  test("should test multiple source scenario") {
    val scenario = ScenarioBuilder
      .streamingLite("scenario1")
      .sources(
        GraphBuilder.source("source1", "start").emptySink("end1", "end", "value" -> "#input"),
        GraphBuilder.source("source2", "start").emptySink("end2", "end", "value" -> "#input")
      )
    val scenarioTestData = ScenarioTestData(List(
      ScenarioTestRecord("source1", Json.fromString("A|1")),
      ScenarioTestRecord("source1", Json.fromString("B|2")),
      ScenarioTestRecord("source2", Json.fromString("C|3")),
    ))

    val results = sample.test(scenario, scenarioTestData)

    results.nodeResults("source1") shouldBe List(NodeResult(ResultContext("A", Map("input" -> 1))), NodeResult(ResultContext("B", Map("input" -> 2))))
    results.nodeResults("source2") shouldBe List(NodeResult(ResultContext("C", Map("input" -> 3))))

    results.externalInvocationResults("end1") shouldBe List(ExternalInvocationResult("A", "end1", 1), ExternalInvocationResult("B", "end1", 2))
    results.externalInvocationResults("end2") shouldBe List(ExternalInvocationResult("C", "end2", 3))
  }

}
