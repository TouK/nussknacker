package pl.touk.nussknacker.engine.lite

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.lite.sample.SampleInputWithListAndMap
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testmode.TestProcess.{
  ExpressionInvocationResult,
  ExternalInvocationResult,
  ResultContext
}

import scala.jdk.CollectionConverters._

class InterpreterTestRunnerTest extends AnyFunSuite with Matchers {

  import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

  test("should test single source scenario") {
    val scenario = ScenarioBuilder
      .streamingLite("scenario1")
      .source("start", "start")
      .enricher("failOnNumber1", "out1", "failOnNumber1", "value" -> "#input".spel)
      .customNode("sum", "sum", "sum", "name" -> "'test'".spel, "value" -> "#input".spel)
      .emptySink("end", "end", "value" -> "#input + ':' + #sum".spel)
    val scenarioTestData = ScenarioTestData(
      List(
        ScenarioTestJsonRecord("start", Json.fromString("A|2")),
        ScenarioTestJsonRecord("start", Json.fromString("B|1")),
        ScenarioTestJsonRecord("start", Json.fromString("C|3")),
      )
    )

    val results = sample.test(scenario, processVersionFor(scenario), scenarioTestData)

    results.nodeResults("start") shouldBe List(
      ResultContext("A", Map("input" -> variable(2))),
      ResultContext("B", Map("input" -> variable(1))),
      ResultContext("C", Map("input" -> variable(3)))
    )

    results.nodeResults("sum") shouldBe List(
      ResultContext("A", Map("input" -> 2, "out1" -> 2).mapValuesNow(variable)),
      ResultContext("C", Map("input" -> 3, "out1" -> 3).mapValuesNow(variable))
    )

    results.nodeResults("end") shouldBe List(
      ResultContext("A", Map("input" -> 2, "out1" -> 2, "sum" -> 2).mapValuesNow(variable)),
      ResultContext("C", Map("input" -> 3, "out1" -> 3, "sum" -> 5).mapValuesNow(variable))
    )

    results.invocationResults("sum") shouldBe List(
      ExpressionInvocationResult("A", "value", variable(2)),
      ExpressionInvocationResult("C", "value", variable(3))
    )

    results.externalInvocationResults("end") shouldBe List(
      ExternalInvocationResult("A", "end", variable("2:2.0")),
      ExternalInvocationResult("C", "end", variable("3:5.0"))
    )
  }

  test("should test multiple source scenario") {
    val scenario = ScenarioBuilder
      .streamingLite("scenario1")
      .sources(
        GraphBuilder.source("source1", "start").emptySink("end1", "end", "value" -> "#input".spel),
        GraphBuilder.source("source2", "start").emptySink("end2", "end", "value" -> "#input".spel)
      )
    val scenarioTestData = ScenarioTestData(
      List(
        ScenarioTestJsonRecord("source1", Json.fromString("A|1")),
        ScenarioTestJsonRecord("source1", Json.fromString("B|2")),
        ScenarioTestJsonRecord("source2", Json.fromString("C|3")),
      )
    )

    val results = sample.test(scenario, processVersionFor(scenario), scenarioTestData)

    results.nodeResults("source1") shouldBe List(
      ResultContext("A", Map("input" -> variable(1))),
      ResultContext("B", Map("input" -> variable(2)))
    )
    results.nodeResults("source2") shouldBe List(ResultContext("C", Map("input" -> variable(3))))

    results.externalInvocationResults("end1") shouldBe List(
      ExternalInvocationResult("A", "end1", variable(1)),
      ExternalInvocationResult("B", "end1", variable(2))
    )
    results.externalInvocationResults("end2") shouldBe List(ExternalInvocationResult("C", "end2", variable(3)))
  }

  test("should accept and run scenario test with parameters") {
    val scenario = ScenarioBuilder
      .streamingLite("scenario1")
      .source("source1", "parametersSupport")
      .emptySink("end", "end", "value" -> "#input".spel)
    val parameterExpressions = Map(
      ParameterName("contextId")        -> Expression(Language.Spel, "'some-ctx-id'"),
      ParameterName("numbers")          -> Expression(Language.Spel, "{1L, 2L, 3L}"),
      ParameterName("additionalParams") -> Expression(Language.Spel, "{unoDosTres: 123}")
    )
    val scenarioTestData = ScenarioTestData("source1", parameterExpressions)
    val results          = sample.test(scenario, processVersionFor(scenario), scenarioTestData)

    results.nodeResults("source1") shouldBe List(
      ResultContext(
        "some-ctx-id",
        Map(
          "input" -> variable(
            SampleInputWithListAndMap(
              "some-ctx-id",
              List(1L, 2L, 3L).asJava,
              Map[String, Any]("unoDosTres" -> 123).asJava
            )
          )
        )
      )
    )
  }

  test("should handle scenario test parameters in test") {
    val scenario = ScenarioBuilder
      .streamingLite("scenario1")
      .source("source1", "parametersSupport")
      .enricher("sumNumbers", "sum", "sumNumbers", "value" -> "#input.numbers".spel)
      .emptySink(
        "end",
        "end",
        "value" -> "#sum + #input.additionalParams.extraValue + #UTIL.largestListElement(#input.numbers)".spel
      )

    val parameterExpressions = Map(
      ParameterName("contextId")        -> Expression(Language.Spel, "'some-ctx-id'"),
      ParameterName("numbers")          -> Expression(Language.Spel, "{1L, 2L, 3L, 4L, 5L}"),
      ParameterName("additionalParams") -> Expression(Language.Spel, "{extraValue: 100}")
    )
    val scenarioTestData = ScenarioTestData("source1", parameterExpressions)
    val results          = sample.test(scenario, processVersionFor(scenario), scenarioTestData)

    results.nodeResults("source1") shouldBe List(
      ResultContext(
        "some-ctx-id",
        Map(
          "input" -> variable(
            SampleInputWithListAndMap(
              "some-ctx-id",
              List(1L, 2L, 3L, 4L, 5L).asJava,
              Map[String, Any]("extraValue" -> 100).asJava
            )
          )
        )
      )
    )

    results.invocationResults("sumNumbers") shouldBe List(
      ExpressionInvocationResult("some-ctx-id", "value", variable(List(1, 2, 3, 4, 5)))
    )

    results.externalInvocationResults("end") shouldBe List(
      ExternalInvocationResult("some-ctx-id", "end", variable(120))
    )
  }

  test("should handle fragment test parameters in test") {
    val fragment = ScenarioBuilder
      .fragment("fragment1", "in" -> classOf[String])
      .filter("filter", "#in != 'stop'".spel)
      .fragmentOutput("fragmentEnd", "output", "out" -> "#in".spel)

    val parameterExpressions = Map(
      ParameterName("in") -> Expression(Language.Spel, "'some-text-id'")
    )
    val scenarioTestData = ScenarioTestData("fragment1", parameterExpressions)
    val results          = sample.test(fragment, processVersionFor(fragment), scenarioTestData)

    results.nodeResults("fragment1") shouldBe List(ResultContext("fragment1", Map("in" -> variable("some-text-id"))))
    results.nodeResults("fragmentEnd") shouldBe List(
      ResultContext("fragment1", Map("in" -> variable("some-text-id"), "out" -> variable("some-text-id")))
    )
    results.invocationResults("fragmentEnd") shouldBe List(
      ExpressionInvocationResult("fragment1", "out", variable("some-text-id"))
    )
    results.exceptions shouldBe empty
  }

  test("should handle errors in fragment output") {
    val fragment = ScenarioBuilder
      .fragment("fragment1", "in" -> classOf[Int])
      .fragmentOutput("fragmentEnd", "output", "out" -> "4 / #in".spel, "out_2" -> "8 / #in".spel)

    val parameterExpressions = Map(
      ParameterName("in") -> Expression(Language.Spel, "0")
    )
    val scenarioTestData = ScenarioTestData("fragment1", parameterExpressions)
    val results          = sample.test(fragment, processVersionFor(fragment), scenarioTestData)

    results.nodeResults("fragment1") shouldBe List(ResultContext("fragment1", Map("in" -> variable(0))))
    results.nodeResults("fragmentEnd") shouldBe List(ResultContext("fragment1", Map("in" -> variable(0))))
    results.exceptions.map(e => (e.context, e.nodeId, e.throwable.getMessage)) shouldBe List(
      (
        ResultContext("fragment1", Map("in" -> variable(0))),
        Some("fragmentEnd"),
        "Expression [4 / #in] evaluation failed, message: / by zero"
      ),
      (
        ResultContext("fragment1", Map("in" -> variable(0))),
        Some("fragmentEnd"),
        "Expression [8 / #in] evaluation failed, message: / by zero"
      )
    )
  }

  private def variable(value: Any): Json = {
    def toJson(v: Any): Json = v match {
      case int: Int      => Json.fromInt(int)
      case lng: Long     => Json.fromLong(lng)
      case str: String   => Json.fromString(str)
      case list: List[_] => Json.fromValues(list.map(toJson))
      case any           => Json.fromString(any.toString)
    }

    Json.obj("pretty" -> toJson(value))
  }

  private def processVersionFor(scenario: CanonicalProcess) = {
    ProcessVersion.empty.copy(processName = scenario.metaData.name)
  }

}
