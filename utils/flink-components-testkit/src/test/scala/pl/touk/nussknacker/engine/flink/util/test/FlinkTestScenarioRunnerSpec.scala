package pl.touk.nussknacker.engine.flink.util.test

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.spel.SpelExpressionEvaluationException
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.concurrent.{ExecutionContext, Future}

class FlinkTestScenarioRunnerSpec extends AnyFunSuite with Matchers with FlinkSpec with ValidatedValuesDetailedMessage {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.Implicits._

  test("should return service invoke value") {
    val input = "input"

    val scenario: CanonicalProcess =
      ScenarioBuilder
        .streaming(getClass.getName)
        .source("start", TestScenarioRunner.testDataSource)
        .enricher("service", "output", TestService.ServiceId, "param" -> "#input")
        .processorEnd("end", TestScenarioRunner.testResultService, "value" -> "#output")

    val runResults =
      TestScenarioRunner
        .flinkBased(config, flinkMiniCluster)
        .withExtraComponents(List(ComponentDefinition(TestService.ServiceId, TestService)))
        .build()
        .runWithData[String, String](scenario, List(input))

    runResults.validValue.successes shouldBe List(input)
  }

  test("should return service invoke mocked value for test runtime mode") {
    val input = "input"

    val scenario: CanonicalProcess =
      ScenarioBuilder
        .streaming(getClass.getName)
        .source("start", TestScenarioRunner.testDataSource)
        .enricher("service", "output", TestService.ServiceId, "param" -> "#input")
        .processorEnd("end", TestScenarioRunner.testResultService, "value" -> "#output")

    val runResults =
      TestScenarioRunner
        .flinkBased(config, flinkMiniCluster)
        .withExtraComponents(List(ComponentDefinition(TestService.ServiceId, TestService)))
        .inTestRuntimeMode
        .build()
        .runWithData[String, String](scenario, List(input))

    runResults.validValue.successes shouldBe List(TestService.MockedValued)
  }

  test("should allow using extra global variables") {
    val scenario =
      ScenarioBuilder
        .streaming(getClass.getName)
        .source("start", TestScenarioRunner.testDataSource)
        .processorEnd("end", TestScenarioRunner.testResultService, "value" -> "#SAMPLE.foo")

    val runResults =
      TestScenarioRunner
        .flinkBased(config, flinkMiniCluster)
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
        .processorEnd("end", TestScenarioRunner.testResultService, "value" -> "#NUMERIC.negate(#input)")

    val runResults =
      TestScenarioRunner
        .flinkBased(config, flinkMiniCluster)
        .build()
        .runWithData[Int, Int](scenario, List(123))

    runResults.validValue.successes shouldBe List(-123)
  }

  test("should handle exception during runtime in test run mode") {
    val scenario =
      ScenarioBuilder
        .streaming(getClass.getName)
        .source("start", TestScenarioRunner.testDataSource)
        .filter("filter", "#input / 0 != 0") // intentional throwing of an exception
        .processorEnd("end", TestScenarioRunner.testResultService, "value" -> "#input")

    val runResults =
      TestScenarioRunner
        .flinkBased(config, flinkMiniCluster)
        .inTestRuntimeMode
        .build()
        .runWithData[Int, Int](scenario, List(10))

    runResults.validValue.errors.map(_.throwable).map { exc =>
      exc.asInstanceOf[SpelExpressionEvaluationException].getMessage
    } shouldBe List(
      "Expression [#input / 0 != 0] evaluation failed, message: / by zero",
    )
  }

  object TestService extends EagerService {

    val ServiceId = "testService"

    val MockedValued = "sample-mocked"

    @MethodToInvoke
    def prepare(@ParamName("param") value: LazyParameter[String]): ServiceInvoker = new ServiceInvoker {

      override def invoke(context: Context)(
          implicit ec: ExecutionContext,
          collector: ServiceInvocationCollector,
          componentUseCase: ComponentUseCase
      ): Future[String] = {
        collector.collect(s"test-service-$value", Option(MockedValued)) {
          Future.successful(value.evaluate(context))
        }
      }

    }

  }

  object SampleHelper {
    def foo: Int = 123
  }

}
