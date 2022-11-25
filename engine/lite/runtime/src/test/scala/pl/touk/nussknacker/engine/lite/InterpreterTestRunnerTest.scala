package pl.touk.nussknacker.engine.lite

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.test.{MultipleSourcesScenarioTestData, ScenarioTestData, TestData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testmode.TestProcess.{ExpressionInvocationResult, ExternalInvocationResult, NodeResult, ResultContext}

import java.nio.charset.StandardCharsets

class InterpreterTestRunnerTest extends AnyFunSuite with Matchers {

  test("should test single source scenario") {
    val scenario = ScenarioBuilder
      .streamingLite("scenario1")
      .source("start", "start")
      .enricher("failOnNumber1", "out1", "failOnNumber1", "value" -> "#input")
      .customNode("sum", "sum", "sum", "name" -> "'test'", "value" -> "#input")
      .emptySink("end", "end", "value" -> "#input + ':' + #sum")
    val testData = ScenarioTestData.newLineSeparated("A|2", "B|1", "C|3")

    val results = sample.test(scenario, testData)

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
    val testData = MultipleSourcesScenarioTestData(
      Map(
        "source1" -> TestData("A|1\nB|2".getBytes(StandardCharsets.UTF_8)),
        "source2" -> TestData("C|3".getBytes(StandardCharsets.UTF_8)),
      ),
      samplesLimit = 2
    )

    val results = sample.test(scenario, testData)

    results.nodeResults("source1") shouldBe List(NodeResult(ResultContext("A", Map("input" -> 1))), NodeResult(ResultContext("B", Map("input" -> 2))))
    results.nodeResults("source2") shouldBe List(NodeResult(ResultContext("C", Map("input" -> 3))))

    results.externalInvocationResults("end1") shouldBe List(ExternalInvocationResult("A", "end1", 1), ExternalInvocationResult("B", "end1", 2))
    results.externalInvocationResults("end2") shouldBe List(ExternalInvocationResult("C", "end2", 3))
  }

}
