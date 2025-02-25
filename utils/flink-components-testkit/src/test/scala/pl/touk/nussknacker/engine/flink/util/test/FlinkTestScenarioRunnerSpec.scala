package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.spel.SpelExpressionEvaluationException
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class FlinkTestScenarioRunnerSpec
    extends AnyFunSuite
    with Matchers
    with ValidatedValuesDetailedMessage
    with BeforeAndAfterAll {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val config = ConfigFactory.empty()

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  test("should return service invoke value") {
    val input = "input"

    val scenario: CanonicalProcess =
      ScenarioBuilder
        .streaming(getClass.getName)
        .source("start", TestScenarioRunner.testDataSource)
        .enricher("service", "output", TestService.ServiceId, "param" -> "#input".spel)
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#output".spel)

    val runResults =
      TestScenarioRunner
        .flinkBased(config, flinkMiniClusterWithServices)
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
        .enricher("service", "output", TestService.ServiceId, "param" -> "#input".spel)
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#output".spel)

    val runResults =
      TestScenarioRunner
        .flinkBased(config, flinkMiniClusterWithServices)
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
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#SAMPLE.foo".spel)

    val runResults =
      TestScenarioRunner
        .flinkBased(config, flinkMiniClusterWithServices)
        .withExtraGlobalVariables(Map("SAMPLE" -> SampleHelper))
        .build()
        .runWithData[String, String](scenario, List("lcl"))

    runResults.validValue.successes shouldBe List(SampleHelper.foo)
  }

  test("should allow using meta variables") {
    val scenario =
      ScenarioBuilder
        .streaming(getClass.getName)
        .source("start", TestScenarioRunner.testDataSource)
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#meta.scenarioLabels".spel)

    val runResults =
      TestScenarioRunner
        .flinkBased(config, flinkMiniClusterWithServices)
        .build()
        .runWithData[String, String](
          scenario,
          List("data"),
          processVersion = ProcessVersion.empty.copy(
            processName = scenario.name,
            labels = List("abc", "def")
          )
        )

    runResults.validValue.successes shouldBe List(List("abc", "def").asJava)
  }

  test("should allow using default global variables") {
    val scenario =
      ScenarioBuilder
        .streaming(getClass.getName)
        .source("start", TestScenarioRunner.testDataSource)
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#NUMERIC.negate(#input)".spel)

    val runResults =
      TestScenarioRunner
        .flinkBased(config, flinkMiniClusterWithServices)
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
        .flinkBased(config, flinkMiniClusterWithServices)
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
          componentUseContext: ComponentUseContext,
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
