package pl.touk.nussknacker.engine.standalone

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import io.dropwizard.metrics5.MetricRegistry
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.api.{Context, JobData, ProcessVersion}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.standalone.api.types.GenericListResultType
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer
import pl.touk.nussknacker.engine.standalone.utils.metrics.dropwizard.DropwizardMetricsProvider
import pl.touk.nussknacker.engine.standalone.utils.metrics.{MetricsProvider, NoOpMetricsProvider}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class StandaloneProcessInterpreterSpec extends FunSuite with Matchers with VeryPatientScalaFutures {

  import spel.Implicits._

  import scala.concurrent.ExecutionContext.Implicits.global


  test("run process in request response mode") {

    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .filter("filter1", "#input.field1 == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .sink("endNodeIID", "#var1", "response-sink")

    val creator = new StandaloneProcessConfigCreator
    val contextId = "context-id"
    val result = runProcess(process, Request1("a", "b"), creator, contextId = Some(contextId))

    result shouldBe Right(List(Response(s"alamakota-$contextId")))
    creator.processorService.invocationsCount.get() shouldBe 1
  }

  test("collect results after split") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
        .split("split",
          GraphBuilder.sink("sink1", "#input.field1", "response-sink"),
          GraphBuilder.sink("sink2", "#input.field2", "response-sink")
        )

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Right(List("a", "b"))
  }

  test("collect metrics") {

    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .filter("filter1", "#input.field1 == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .sink("endNodeIID", "#var1", "response-sink")

    val creator = new StandaloneProcessConfigCreator
    val metricRegistry = new MetricRegistry

    val interpreter = prepareInterpreter(process, creator, metricRegistry)
    interpreter.open(JobData(process.metaData, ProcessVersion.empty))
    val contextId = "context-id"
    val result = interpreter.invoke(Request1("a", "b"), Some(contextId)).futureValue

    result shouldBe Right(List(Response(s"alamakota-$contextId")))
    creator.processorService.invocationsCount.get() shouldBe 1

    eventually {
      metricRegistry.getGauges().get(MetricRegistry.name("invocation", "success", "instantRate")
              .tagged("processId", "proc1")).getValue.asInstanceOf[Double] should not be 0
      metricRegistry.getHistograms().get(MetricRegistry.name("invocation", "success", "histogram")
              .tagged("processId", "proc1")).getCount shouldBe 1
    }

    interpreter.close()
  }

  test("collect results after element split") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .customNode("split", "outPart", "splitter", "parts" -> "#input.toList()")
      .buildSimpleVariable("var1", "var1", "#outPart")
      .sink("sink1", "#outPart", "response-sink")

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Right(List("a", "b"))
  }

  test("init call open method for service") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .enricher("enricherWithOpenService", "response", "enricherWithOpenService")
      .sink("sink1", "#response.field1", "response-sink")

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Right(List("initialized!"))
  }

  test("collect metrics for individual services") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .enricher("enricherWithOpenService", "response", "enricherWithOpenService")
      .sink("sink1", "#response.field1", "response-sink")

    val metricRegistry = new MetricRegistry

    val interpreter = prepareInterpreter(process, new StandaloneProcessConfigCreator, metricRegistry = metricRegistry)
    interpreter.open(JobData(process.metaData, ProcessVersion.empty))
    val result = interpreter.invoke(Request1("a", "b")).futureValue

    result shouldBe Right(List("initialized!"))

    eventually {
      metricRegistry.getGauges().get(MetricRegistry.name("invocation", "success", "instantRate")
        .tagged("processId", "proc1")).getValue.asInstanceOf[Double] should not be 0
      metricRegistry.getHistograms().get(MetricRegistry.name("invocation", "success", "histogram")
        .tagged("processId", "proc1")).getCount shouldBe 1
      metricRegistry.getGauges().get(MetricRegistry.name("service", "OK", "instantRate")
        .tagged("processId", "proc1", "serviceName", "enricherWithOpenService")).getValue.asInstanceOf[Double] should not be 0
      metricRegistry.getHistograms().get(MetricRegistry.name("service", "OK", "histogram")
              .tagged("processId", "proc1", "serviceName", "enricherWithOpenService")).getCount shouldBe 1
    }
    interpreter.close()
  }

  test("run process using custom node with ContextTransformation API") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .customNode("extract", "extracted", "extractor", "expression" -> "#input.field2")
      .sink("sink1", "#extracted", "response-sink")

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Right(List("b"))
  }

  test("collects answers from parameters") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1 + 'd'")

    val result = runProcess(process, Request1("abc", "b"))

    result shouldBe Right(List("abcd withRandomString"))
  }

  test("recognizes output types") {

    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1 + 'd'")


    val interpreter = prepareInterpreter(process = process)
    interpreter.sinkTypes shouldBe Map("endNodeIID" -> Typed[String])

    val process2 = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .sink("endNodeIID", "{'str': #input.toString(), 'int': 15}", "response-sink")


    val interpreter2 = prepareInterpreter(process = process2)
    interpreter2.sinkTypes shouldBe Map("endNodeIID" -> TypedObjectTypingResult(Map("str" -> Typed[String], "int" -> Typed[java.lang.Integer])))

  }

  test("handles exceptions in sink") {
    val process = EspProcessBuilder
      .id("exception-in-sink")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .sink("sink", "#input.field1", "failing-sink", "fail" -> "true")

    val creator = new StandaloneProcessConfigCreator
    val contextId = "context-id"
    val result = runProcess(process, Request1("a", "b"), creator, contextId = Some(contextId))

    result shouldBe Left(NonEmptyList.of(
      EspExceptionInfo(Some("sink"),
        SinkException("FailingSink failed"),
        Context( "context-id", Map("input" -> Request1("a","b")), None))
    ))
  }

  def runProcess(process: EspProcess,
                 input: Any,
                 creator: StandaloneProcessConfigCreator = new StandaloneProcessConfigCreator,
                 metricRegistry: MetricRegistry = new MetricRegistry,
                 contextId: Option[String] = None): GenericListResultType[Any] = {
    val interpreter = prepareInterpreter(
      process = process,
      creator = creator,
      metricRegistry = metricRegistry
    )
    interpreter.open(JobData(process.metaData,ProcessVersion.empty))
    val result = interpreter.invoke(input, contextId).futureValue
    interpreter.close()
    result
  }

  def prepareInterpreter(process: EspProcess,
                         creator: StandaloneProcessConfigCreator,
                         metricRegistry: MetricRegistry): StandaloneProcessInterpreter = {
    prepareInterpreter(process, creator, new DropwizardMetricsProvider(metricRegistry))
  }

  def prepareInterpreter(process: EspProcess,
                         creator: StandaloneProcessConfigCreator = new StandaloneProcessConfigCreator,
                         metricsProvider: MetricsProvider = NoOpMetricsProvider): StandaloneProcessInterpreter = {
    val simpleModelData = LocalModelData(ConfigFactory.load(), creator)
    val ctx = new StandaloneContextPreparer(metricsProvider)

    val maybeinterpreter = StandaloneProcessInterpreter(process, ctx, simpleModelData)

    maybeinterpreter shouldBe 'valid
    val interpreter = maybeinterpreter.toOption.get
    interpreter
  }

}