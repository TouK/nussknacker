package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ComponentUseCase.EngineRuntime
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.spel.SpelExpressionEvaluationException
import pl.touk.nussknacker.engine.util.functions.DateUtils
import pl.touk.nussknacker.engine.util.test.{RunResult, TestScenarioRunner}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.Future

class LiteTestScenarioRunnerSpec extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage with Inside {

  import LiteTestScenarioRunner._

  test("should test custom component with lite") {
    val scenario = ScenarioBuilder
      .streamingLite("t1")
      .source("source", TestScenarioRunner.testDataSource)
      .enricher("customByHand", "o1", "customByHand", "param" -> "#input".spel)
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#o1".spel)

    val runner = new LiteTestScenarioRunner(
      List(ComponentDefinition("customByHand", new CustomComponent("myPrefix"))),
      Map.empty,
      ConfigFactory.empty,
      EngineRuntime
    )

    val result = runner.runWithData[String, java.util.List[String]](scenario, List("t1"))
    result.validValue shouldBe RunResult.success("myPrefix:t1")
  }

  test("should access source property") {
    val scenario = ScenarioBuilder
      .streamingLite("t1")
      .source("source", TestScenarioRunner.testDataSource)
      .buildVariable("v", "v", "varField" -> "#input.field".spel)
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#v.varField".spel)

    val runner = new LiteTestScenarioRunner(List.empty, Map.empty, ConfigFactory.empty(), EngineRuntime)

    val result = runner.runWithData[SourceData, String](scenario, List(SourceData("abc")))
    result.validValue shouldBe RunResult.success("abc")
  }

  test("should return service invoke value") {
    val input = "input"

    val scenario =
      ScenarioBuilder
        .streamingLite("stream")
        .source("source", TestScenarioRunner.testDataSource)
        .enricher("service", "output", TestService.ServiceId, "param" -> "#input".spel)
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#output".spel)

    val runResults =
      TestScenarioRunner
        .liteBased(ConfigFactory.empty())
        .withExtraComponents(List(ComponentDefinition(TestService.ServiceId, TestService)))
        .build()
        .runWithData(scenario, List(input))

    runResults.validValue.successes shouldBe List(input)
  }

  test("should return service invoke mocked value for test runtime mode") {
    val input = "input"

    val scenario =
      ScenarioBuilder
        .streamingLite("stream")
        .source("source", TestScenarioRunner.testDataSource)
        .enricher("service", "output", TestService.ServiceId, "param" -> "#input".spel)
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#output".spel)

    val runResults =
      TestScenarioRunner
        .liteBased(ConfigFactory.empty())
        .withExtraComponents(List(ComponentDefinition(TestService.ServiceId, TestService)))
        .inTestRuntimeMode
        .build()
        .runWithData(scenario, List(input))

    runResults.validValue.successes shouldBe List(TestService.MockedValued)
  }

  test("should allow using extra global variables") {
    val scenario =
      ScenarioBuilder
        .streaming(getClass.getName)
        .source("start", TestScenarioRunner.testDataSource)
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#SAMPLE.foo".spel)

    val runResults =
      TestScenarioRunner
        .liteBased()
        .withExtraGlobalVariables(Map("SAMPLE" -> SampleHelper))
        .build()
        .runWithData[String, String](scenario, List("lcl"))

    runResults.validValue.successes shouldBe List(SampleHelper.foo)
  }

  test("should allow using default global variables") {
    val scenario =
      ScenarioBuilder
        .streaming(getClass.getName)
        .source("start", TestScenarioRunner.testDataSource)
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#NUMERIC.negate(#input)".spel)

    val runResults =
      TestScenarioRunner
        .liteBased()
        .build()
        .runWithData[Int, Int](scenario, List(123))

    runResults.validValue.successes shouldBe List(-123)
  }

  test("should handle exception during runtime in test run mode") {
    val scenario =
      ScenarioBuilder
        .streaming(getClass.getName)
        .source("start", TestScenarioRunner.testDataSource)
        .filter("filter", "#input / 0 != 0".spel) // intentional throwing of an exception
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#input".spel)

    val runResults =
      TestScenarioRunner
        .liteBased(ConfigFactory.empty())
        .inTestRuntimeMode
        .build()
        .runWithData[Int, Int](scenario, List(10))

    runResults.validValue.errors.map(_.throwable).map { exc =>
      exc.asInstanceOf[SpelExpressionEvaluationException].getMessage
    } shouldBe List(
      "Expression [#input / 0 != 0] evaluation failed, message: / by zero",
    )
  }

}

private case class SourceData(field: String)

class CustomComponent(prefix: String) extends Service {
  @MethodToInvoke
  def invoke(@ParamName("param") input: String): Future[String] = Future.successful(s"$prefix:$input")
}

object SampleHelper {
  def foo: Int = 123
}
