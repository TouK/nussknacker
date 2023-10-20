package pl.touk.nussknacker.engine.flink.util.test

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.spel.SpelExpressionEvaluationException
import pl.touk.nussknacker.engine.util.functions._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.time.{Clock, Instant, ZoneId}
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

  test("should allowing use global variable - date helper") {
    val now        = Instant.now()
    val dateHelper = new DateUtils(Clock.fixed(now, ZoneId.systemDefault()))

    val scenario: CanonicalProcess =
      ScenarioBuilder
        .streaming(getClass.getName)
        .source("start", TestScenarioRunner.testDataSource)
        .processorEnd("end", TestScenarioRunner.testResultService, "value" -> "#DATE.now.toString")

    val runResults =
      TestScenarioRunner
        .flinkBased(config, flinkMiniCluster)
        .withExtraGlobalVariables(Map("DATE" -> dateHelper))
        .build()
        .runWithData[String, String](scenario, List("lcl"))

    runResults.validValue.successes shouldBe List(now.toString)
  }

  // TODO: FlinkTestScenarioRunner doesn't collect errors, see FlinkTestScenarioRunner.collectResults
  ignore("should catch exception during compilation in test run mode") {
    val scenario =
      ScenarioBuilder
        .streaming(getClass.getName)
        .source("start", TestScenarioRunner.testDataSource)
        .filter("filter", "#input / 0 != 0") // intentional throwing of an exception
        .processorEnd("end", TestScenarioRunner.testResultService, "value" -> "#input")

    val runResults =
      TestScenarioRunner
        .flinkBased(config, flinkMiniCluster)
        .build()
        .runWithData[Int, Int](scenario, List(10))

    runResults.validValue.errors.collect { case NuExceptionInfo(_, e: SpelExpressionEvaluationException, _) =>
      e.getMessage
    } shouldBe List(
      "Expression [#input / 0 != 0] evaluation failed, message: divide by zero"
    )
  }

  object TestService extends EagerService {

    val ServiceId = "testService"

    val MockedValued = "sample-mocked"

    @MethodToInvoke
    def invoke(@ParamName("param") value: LazyParameter[String]): ServiceInvoker = new ServiceInvoker {

      override def invokeService(params: Map[String, Any])(
          implicit ec: ExecutionContext,
          collector: ServiceInvocationCollector,
          contextId: ContextId,
          componentUseCase: ComponentUseCase
      ): Future[String] = {
        collector.collect(s"test-service-$value", Option(MockedValued)) {
          Future.successful(params("param").asInstanceOf[String])
        }
      }

    }

  }

}
