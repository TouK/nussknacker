package pl.touk.nussknacker.engine.lite

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.lite.sample.SampleInputWithListAndMap
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.{ExpressionInvocationResult, ExternalInvocationResult}

import java.time.Instant
import scala.jdk.CollectionConverters._

class InterpreterTestRunnerTest extends AnyFunSuite with Matchers {

  import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

  private val mockedTimestamp = Instant.now()

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

    nodeResults(results, "start") shouldBe List(
      ("A", Map("input" -> variable(2))),
      ("B", Map("input" -> variable(1))),
      ("C", Map("input" -> variable(3)))
    )

    nodeResults(results, "sum") shouldBe List(
      ("A", Map("input" -> 2, "out1" -> 2).mapValuesNow(variable)),
      ("C", Map("input" -> 3, "out1" -> 3).mapValuesNow(variable))
    )

    nodeResults(results, "end") shouldBe List(
      ("A", Map("input" -> 2, "out1" -> 2, "sum" -> 2).mapValuesNow(variable)),
      ("C", Map("input" -> 3, "out1" -> 3, "sum" -> 5).mapValuesNow(variable))
    )

    results.invocationResults("sum").map(withMockedTimestamp) shouldBe List(
      ExpressionInvocationResult("A", mockedTimestamp, "value", variable(2)),
      ExpressionInvocationResult("C", mockedTimestamp, "value", variable(3))
    )

    results.externalInvocationResults("end").map(withMockedTimestamp) shouldBe List(
      ExternalInvocationResult("A", mockedTimestamp, "end", variable("2:2.0")),
      ExternalInvocationResult("C", mockedTimestamp, "end", variable("3:5.0"))
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

    nodeResults(results, "source1") shouldBe List(
      ("A", Map("input" -> variable(1))),
      ("B", Map("input" -> variable(2)))
    )
    nodeResults(results, "source2") shouldBe List(("C", Map("input" -> variable(3))))

    results.externalInvocationResults("end1").map(withMockedTimestamp) shouldBe List(
      ExternalInvocationResult("A", mockedTimestamp, "end1", variable(1)),
      ExternalInvocationResult("B", mockedTimestamp, "end1", variable(2))
    )
    results.externalInvocationResults("end2").map(withMockedTimestamp) shouldBe List(
      ExternalInvocationResult("C", mockedTimestamp, "end2", variable(3))
    )
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

    nodeResults(results, "source1") shouldBe List(
      (
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

    nodeResults(results, "source1") shouldBe List(
      (
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

    results.invocationResults("sumNumbers").map(withMockedTimestamp) shouldBe List(
      ExpressionInvocationResult("some-ctx-id", mockedTimestamp, "value", variable(List(1, 2, 3, 4, 5)))
    )

    results.externalInvocationResults("end").map(withMockedTimestamp) shouldBe List(
      ExternalInvocationResult("some-ctx-id", mockedTimestamp, "end", variable(120))
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

    nodeResults(results, "fragment1") shouldBe List(("fragment1", Map("in" -> variable("some-text-id"))))
    nodeResults(results, "fragmentEnd") shouldBe List(
      ("fragment1", Map("in" -> variable("some-text-id"), "out" -> variable("some-text-id")))
    )
    results.invocationResults("fragmentEnd").map(withMockedTimestamp) shouldBe List(
      ExpressionInvocationResult("fragment1", mockedTimestamp, "out", variable("some-text-id"))
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

    nodeResults(results, "fragment1") shouldBe List(("fragment1", Map("in" -> variable(0))))
    nodeResults(results, "fragmentEnd") shouldBe List(("fragment1", Map("in" -> variable(0))))
    results.exceptions.map(e => ((e.context.id, e.context.variables), e.nodeId, e.throwable.getMessage)) shouldBe List(
      (
        ("fragment1", Map("in" -> variable(0))),
        Some("fragmentEnd"),
        "Expression [4 / #in] evaluation failed, message: / by zero"
      ),
      (
        ("fragment1", Map("in" -> variable(0))),
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

  private def nodeResults[T](results: TestProcess.TestResults[T], key: String) =
    results.nodeResults(key).map(r => (r.id, r.variables))

  private def withMockedTimestamp(result: ExpressionInvocationResult[Json]) =
    result.copy(timestamp = mockedTimestamp)

  private def withMockedTimestamp(result: ExternalInvocationResult[Json]) =
    result.copy(timestamp = mockedTimestamp)

}
