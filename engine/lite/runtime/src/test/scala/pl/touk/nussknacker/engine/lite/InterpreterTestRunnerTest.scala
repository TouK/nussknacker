package pl.touk.nussknacker.engine.lite

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testmode.TestProcess.{ExpressionInvocationResult, ExternalInvocationResult}
import pl.touk.nussknacker.engine.lite.sample.SampleInputWithListAndMap

import scala.jdk.CollectionConverters._

class InterpreterTestRunnerTest extends AnyFunSuite with Matchers {

  test("should test single source scenario") {
    val scenario = ScenarioBuilder
      .streamingLite("scenario1")
      .source("start", "start")
      .enricher("failOnNumber1", "out1", "failOnNumber1", "value" -> "#input")
      .customNode("sum", "sum", "sum", "name" -> "'test'", "value" -> "#input")
      .emptySink("end", "end", "value" -> "#input + ':' + #sum")
    val scenarioTestData = ScenarioTestData(
      List(
        ScenarioTestJsonRecord("start", Json.fromString("A|2")),
        ScenarioTestJsonRecord("start", Json.fromString("B|1")),
        ScenarioTestJsonRecord("start", Json.fromString("C|3")),
      )
    )

    val results = sample.test(scenario, scenarioTestData)

    results.nodeResults("start") shouldBe List(
      Context("A", Map("input" -> 2)),
      Context("B", Map("input" -> 1)),
      Context("C", Map("input" -> 3))
    )
    results.nodeResults("sum") shouldBe List(
      Context("A", Map("input" -> 2, "out1" -> 2)),
      Context("C", Map("input" -> 3, "out1" -> 3))
    )
    results.nodeResults("end") shouldBe List(
      Context("A", Map("input" -> 2, "out1" -> 2, "sum" -> 2)),
      Context("C", Map("input" -> 3, "out1" -> 3, "sum" -> 5))
    )

    results.invocationResults("sum") shouldBe List(
      ExpressionInvocationResult("A", "value", 2),
      ExpressionInvocationResult("C", "value", 3)
    )

    results.externalInvocationResults("end") shouldBe List(
      ExternalInvocationResult("A", "end", "2:2.0"),
      ExternalInvocationResult("C", "end", "3:5.0")
    )
  }

  test("should test multiple source scenario") {
    val scenario = ScenarioBuilder
      .streamingLite("scenario1")
      .sources(
        GraphBuilder.source("source1", "start").emptySink("end1", "end", "value" -> "#input"),
        GraphBuilder.source("source2", "start").emptySink("end2", "end", "value" -> "#input")
      )
    val scenarioTestData = ScenarioTestData(
      List(
        ScenarioTestJsonRecord("source1", Json.fromString("A|1")),
        ScenarioTestJsonRecord("source1", Json.fromString("B|2")),
        ScenarioTestJsonRecord("source2", Json.fromString("C|3")),
      )
    )

    val results = sample.test(scenario, scenarioTestData)

    results.nodeResults("source1") shouldBe List(
      Context("A", Map("input" -> 1)),
      Context("B", Map("input" -> 2))
    )
    results.nodeResults("source2") shouldBe List(Context("C", Map("input" -> 3)))

    results.externalInvocationResults("end1") shouldBe List(
      ExternalInvocationResult("A", "end1", 1),
      ExternalInvocationResult("B", "end1", 2)
    )
    results.externalInvocationResults("end2") shouldBe List(ExternalInvocationResult("C", "end2", 3))
  }

  test("should accept and run scenario test with parameters") {
    val scenario = ScenarioBuilder
      .streamingLite("scenario1")
      .source("source1", "parametersSupport")
      .emptySink("end", "end", "value" -> "#input")
    val parameterExpressions = Map(
      ParameterName("contextId")        -> Expression(Language.Spel, "'some-ctx-id'"),
      ParameterName("numbers")          -> Expression(Language.Spel, "{1L, 2L, 3L}"),
      ParameterName("additionalParams") -> Expression(Language.Spel, "{unoDosTres: 123}")
    )
    val scenarioTestData = ScenarioTestData("source1", parameterExpressions)
    val results          = sample.test(scenario, scenarioTestData)

    results.nodeResults("source1") shouldBe List(
      Context(
        "some-ctx-id",
        Map(
          "input" -> SampleInputWithListAndMap(
            "some-ctx-id",
            List(1L, 2L, 3L).asJava,
            Map[String, Any]("unoDosTres" -> 123).asJava
          )
        )
      )
    )
  }

  test("should handle scenario test parameters in test") {
    val scenario = ScenarioBuilder
      .streamingLite("scenario1")
      .source("source1", "parametersSupport")
      .enricher("sumNumbers", "sum", "sumNumbers", "value" -> "#input.numbers")
      .emptySink(
        "end",
        "end",
        "value" -> "#sum + #input.additionalParams.extraValue + #UTIL.largestListElement(#input.numbers)"
      )

    val parameterExpressions = Map(
      ParameterName("contextId")        -> Expression(Language.Spel, "'some-ctx-id'"),
      ParameterName("numbers")          -> Expression(Language.Spel, "{1L, 2L, 3L, 4L, 5L}"),
      ParameterName("additionalParams") -> Expression(Language.Spel, "{extraValue: 100}")
    )
    val scenarioTestData = ScenarioTestData("source1", parameterExpressions)
    val results          = sample.test(scenario, scenarioTestData)

    results.nodeResults("source1") shouldBe List(
      Context(
        "some-ctx-id",
        Map(
          "input" -> SampleInputWithListAndMap(
            "some-ctx-id",
            List(1L, 2L, 3L, 4L, 5L).asJava,
            Map[String, Any]("extraValue" -> 100).asJava
          )
        )
      )
    )
    results.invocationResults("sumNumbers") shouldBe List(
      ExpressionInvocationResult("some-ctx-id", "value", List[java.lang.Long](1, 2, 3, 4, 5).asJava)
    )
    results.externalInvocationResults("end") shouldBe List(ExternalInvocationResult("some-ctx-id", "end", 120))
  }

  test("should handle fragment test parameters in test") {
    val fragment = ScenarioBuilder
      .fragment("fragment1", "in" -> classOf[String])
      .filter("filter", "#in != 'stop'")
      .fragmentOutput("fragmentEnd", "output", "out" -> "#in")

    val parameterExpressions = Map(
      ParameterName("in") -> Expression(Language.Spel, "'some-text-id'")
    )
    val scenarioTestData = ScenarioTestData("fragment1", parameterExpressions)
    val results          = sample.test(fragment, scenarioTestData)

    results.nodeResults("fragment1") shouldBe List(Context("fragment1", Map("in" -> "some-text-id")))
    results.nodeResults("fragmentEnd") shouldBe List(
      Context("fragment1", Map("in" -> "some-text-id", "out" -> "some-text-id"))
    )
    results.invocationResults("fragmentEnd") shouldBe List(
      ExpressionInvocationResult("fragment1", "out", "some-text-id")
    )
    results.exceptions shouldBe empty
  }

  test("should handle errors in fragment output") {
    val fragment = ScenarioBuilder
      .fragment("fragment1", "in" -> classOf[Int])
      .fragmentOutput("fragmentEnd", "output", "out" -> "4 / #in", "out_2" -> "8 / #in")

    val parameterExpressions = Map(
      ParameterName("in") -> Expression(Language.Spel, "0")
    )
    val scenarioTestData = ScenarioTestData("fragment1", parameterExpressions)
    val results          = sample.test(fragment, scenarioTestData)

    results.nodeResults("fragment1") shouldBe List(Context("fragment1", Map("in" -> 0)))
    results.nodeResults("fragmentEnd") shouldBe List(Context("fragment1", Map("in" -> 0)))
    results.exceptions.map(e => (e.context, e.nodeComponentInfo.map(_.nodeId), e.throwable.getMessage)) shouldBe List(
      (
        Context("fragment1", Map("in" -> 0), None),
        Some("fragmentEnd"),
        "Expression [4 / #in] evaluation failed, message: / by zero"
      ),
      (
        Context("fragment1", Map("in" -> 0), None),
        Some("fragmentEnd"),
        "Expression [8 / #in] evaluation failed, message: / by zero"
      )
    )
  }

}
