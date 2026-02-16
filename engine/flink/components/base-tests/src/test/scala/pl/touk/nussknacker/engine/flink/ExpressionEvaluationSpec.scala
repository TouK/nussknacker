package pl.touk.nussknacker.engine.flink

import cats.data.NonEmptyList
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.EmptyMandatoryParameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner.FlinkTestScenarioRunnerExt
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.test.{RunListResult, TestScenarioRunner}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

class ExpressionEvaluationSpec extends AnyFunSuite with FlinkSpec with Matchers {

  test("should run scenario when sink value is empty spelTemplate expression") {
    val scenario = createScenario(Expression.spelTemplate(""))

    val runResults = runScenario(scenario, List(""))
    runResults.validValue shouldBe successfulRun(List(""))
  }

  test("should run scenario when sink value is non-empty spelTemplate expression") {
    val scenario = createScenario(Expression.spelTemplate("value"))

    val runResults = runScenario(scenario, List(""))
    runResults.validValue shouldBe successfulRun(List("value"))
  }

  test("should run scenario when sink value is empty string spel expression") {
    val scenario = createScenario(Expression.spel("''"))

    val runResults = runScenario(scenario, List(""))
    runResults.validValue shouldBe successfulRun(List(""))
  }

  test("should run scenario when sink value is non-empty string spel expression") {
    val scenario = createScenario(Expression.spel("'value'"))

    val runResults = runScenario(scenario, List(""))
    runResults.validValue shouldBe successfulRun(List("value"))
  }

  test("should return error when sink value is empty spel expression") {
    val scenario = createScenario(Expression.spel(""))

    val result = runScenario(scenario, List(""))
    result.invalidValue should matchPattern {
      case NonEmptyList(EmptyMandatoryParameter(_, _, ParameterName("value"), NodeId("end")), Nil) =>
    }
  }

  private def createScenario(
      sinkValue: Expression,
  ) = {
    ScenarioBuilder
      .streaming(getClass.getName)
      .source("start", TestScenarioRunner.testDataSource)
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> sinkValue)
  }

  private def runScenario(scenario: CanonicalProcess, data: List[String]) = {
    TestScenarioRunner
      .flinkBased(modelConfig, flinkMiniCluster)
      .build()
      .runWithData[String, String](scenario, data)
  }

  private def successfulRun[T](successes: List[T]) = RunListResult(Nil, successes)

}
